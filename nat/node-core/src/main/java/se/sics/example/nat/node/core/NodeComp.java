/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * ToolsExamples is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.example.nat.node.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import se.sics.example.nat.node.msg.NodeMsg;
import java.util.Set;
import java.util.UUID;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.example.nat.node.event.Pinged;
import se.sics.example.nat.node.util.NodeView;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.msg.CroupierSample;
import se.sics.p2ptoolbox.croupier.msg.CroupierUpdate;
import se.sics.p2ptoolbox.util.Container;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdate;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NodeComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NodeComp.class);
    private String logPrefix = "";

    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    private Positive<CroupierPort> croupier = requires(CroupierPort.class);
    private Positive<SelfAddressUpdatePort> selfUpdate = requires(SelfAddressUpdatePort.class);
    private Negative<SelfViewUpdatePort> croupierViewUpdate = provides(SelfViewUpdatePort.class);
    private Negative<NodePort> nodePort = provides(NodePort.class);

    private final NodeKCWrapper config;
    private DecoratedAddress self;
    private Map<BasicAddress, Pair<DecoratedAddress, Integer>> ping = new HashMap<>();
    private Map<BasicAddress, DecoratedAddress> ponged = new HashMap<>();
    private Map<BasicAddress, DecoratedAddress> missed = new HashMap<>();
    private Set<String> unfeasible = new HashSet<>();

    private UUID pingTId;
    private UUID statusTId;

    public NodeComp(NodeInit init) {
        config = init.config;
        self = init.self;
        logPrefix = "<nid:" + config.system.id + "> ";
        LOG.info("{}initiating with self:{}",
                new Object[]{logPrefix, self});

        subscribe(handleStart, control);
        subscribe(handleSelfUpdate, selfUpdate);
        subscribe(handleStatusCheck, timer);
        subscribe(handlePingTimeout, timer);
        subscribe(handleCroupierSample, croupier);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handlePinged, nodePort);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting", logPrefix);
            scheduleStatusCheck();
            checkStart();
            trigger(CroupierUpdate.update(new NodeView()), croupierViewUpdate);
        }
    };

    Handler handleSelfUpdate = new Handler<SelfAddressUpdate>() {
        @Override
        public void handle(SelfAddressUpdate update) {
            LOG.info("{}update self:{}", logPrefix, update.self);
            self = update.self;
            checkStart();
        }
    };

    private void checkStart() {
        if (self.getTrait(NatedTrait.class).type.equals(Nat.Type.OPEN)
                || self.getTrait(NatedTrait.class).type.equals(Nat.Type.NAT)) {
            schedulePing();
        } else {
            LOG.error("{}unhandled address type:{}", logPrefix, self.getTrait(NatedTrait.class).type);
            throw new RuntimeException("unhandled address type" + self.getTrait(NatedTrait.class).type);
        }
    }

    Handler handleStatusCheck = new Handler<PeriodicStatusCheck>() {
        @Override
        public void handle(PeriodicStatusCheck event) {
            LOG.info("{}pending:{} ponged:{} missed:{} unfeasible:{}",
                    new Object[]{logPrefix, ping.keySet(), ponged.values(), missed.values(), unfeasible});
        }
    };

    Handler handleCroupierSample = new Handler<CroupierSample<NodeView>>() {
        @Override
        public void handle(CroupierSample<NodeView> sample) {
            LOG.trace("{}public sample:{}", new Object[]{logPrefix, sample.publicSample});
            LOG.trace("{}private sample:{}", new Object[]{logPrefix, sample.privateSample});
            selectPingTargets(sample.publicSample);
            selectPingTargets(sample.privateSample);
        }
    };

    private void selectPingTargets(Set<Container<DecoratedAddress, NodeView>> sample) {
        for (Container<DecoratedAddress, NodeView> node : sample) {
            if (ping.containsKey(node.getSource().getBase()) || ponged.containsKey(node.getSource().getBase())
                    || missed.containsKey(node.getSource().getBase())) {
                continue;
            }
            ping.put(node.getSource().getBase(), Pair.with(node.getSource(), config.pingRetry));
        }
    }

    Handler handlePingTimeout = new Handler<PeriodicPing>() {

        @Override
        public void handle(PeriodicPing event) {
            Iterator<Pair<DecoratedAddress, Integer>> it = ping.values().iterator();
            while (it.hasNext()) {
                Pair<DecoratedAddress, Integer> target = it.next();
                if (ponged.containsKey(target.getValue0().getBase())) {
                    it.remove();
                    continue;
                }
                if (target.getValue1() > 0) {
                    ping(target.getValue0());
                } else {
                    it.remove();
                    missed.put(target.getValue0().getBase(), target.getValue0());
                }
            }
            for (Map.Entry<BasicAddress, Pair<DecoratedAddress, Integer>> target : ping.entrySet()) {
                ping.put(target.getKey(), Pair.with(target.getValue().getValue0(), target.getValue().getValue1() - 1));
            }
        }
    };

    private void ping(DecoratedAddress target) {
        DecoratedHeader<DecoratedAddress> pingHeader = new DecoratedHeader(self, target, Transport.UDP);
        ContentMsg pingMsg = new BasicContentMsg(pingHeader, new NodeMsg.Ping());
        LOG.debug("{}pinging from:{} to:{}", new Object[]{logPrefix, self, target});
        trigger(pingMsg, network);
    }

    Handler handlePinged = new Handler<Pinged.Request>() {
        @Override
        public void handle(Pinged.Request req) {
            answer(req, req.answer(self, ponged.values()));
        }
    };

    ClassMatchedHandler handlePing
            = new ClassMatchedHandler<NodeMsg.Ping, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NodeMsg.Ping>>() {
                @Override
                public void handle(NodeMsg.Ping content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NodeMsg.Ping> container) {
                    LOG.debug("{}ping from:{} on:{}",
                            new Object[]{logPrefix, container.getSource(), container.getDestination()});
                    DecoratedHeader<DecoratedAddress> pongHeader = new DecoratedHeader(self, container.getSource(), Transport.UDP);
                    ContentMsg pongMsg = new BasicContentMsg(pongHeader, new NodeMsg.Pong());
                    trigger(pongMsg, network);
                }
            };

    ClassMatchedHandler handlePong
            = new ClassMatchedHandler<NodeMsg.Pong, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NodeMsg.Pong>>() {
                @Override
                public void handle(NodeMsg.Pong content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NodeMsg.Pong> container) {
                    LOG.debug("{}pong from:{} on:{}",
                            new Object[]{logPrefix, container.getSource(), container.getDestination()});
                    ponged.put(container.getSource().getBase(), container.getSource());
                }
            };

    public static class NodeInit extends Init<NodeComp> {

        public final NodeKCWrapper config;
        public final DecoratedAddress self;

        public NodeInit(KConfigCore configCore, DecoratedAddress self) {
            this.config = new NodeKCWrapper(configCore);
            this.self = self;
        }
    }

    private void schedulePing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(config.pingTimeout, config.pingTimeout);
        PeriodicPing pp = new PeriodicPing(spt);
        spt.setTimeoutEvent(pp);
        trigger(spt, timer);
        pingTId = pp.getTimeoutId();
    }

    public static class PeriodicPing extends Timeout {

        public PeriodicPing(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }

    private void scheduleStatusCheck() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(config.internalStatusCheck, config.internalStatusCheck);
        PeriodicStatusCheck psc = new PeriodicStatusCheck(spt);
        spt.setTimeoutEvent(psc);
        trigger(spt, timer);
        statusTId = psc.getTimeoutId();
    }

    public static class PeriodicStatusCheck extends Timeout {

        public PeriodicStatusCheck(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }

}

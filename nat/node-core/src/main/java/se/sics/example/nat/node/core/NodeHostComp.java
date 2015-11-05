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

import java.util.UUID;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.networkmngr.NetworkKCWrapper;
import se.sics.ktoolbox.networkmngr.NetworkMngrComp;
import se.sics.ktoolbox.networkmngr.NetworkMngrPort;
import se.sics.ktoolbox.networkmngr.events.Bind;
import se.sics.ktoolbox.networkmngr.events.NetworkMngrReady;
import se.sics.ktoolbox.overlaymngr.OverlayMngrComp;
import se.sics.ktoolbox.overlaymngr.OverlayMngrComp.OverlayMngrInit;
import se.sics.ktoolbox.overlaymngr.OverlayMngrPort;
import se.sics.ktoolbox.overlaymngr.events.OMngrCroupier;
import se.sics.nat.NatDetectionComp;
import se.sics.nat.NatDetectionComp.NatDetectionInit;
import se.sics.nat.NatDetectionPort;
import se.sics.nat.NatTraverserComp;
import se.sics.nat.NatTraverserComp.NatTraverserInit;
import se.sics.nat.stun.NatDetected;
import se.sics.nat.util.NatStatus;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
import se.sics.p2ptoolbox.util.status.Ready;
import se.sics.p2ptoolbox.util.status.StatusPort;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdate;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NodeHostComp extends ComponentDefinition {

    private Logger LOG = LoggerFactory.getLogger(NodeHostComp.class);
    private String logPrefix = "";

    private Positive<Timer> timer = requires(Timer.class);

    private final NodeKCWrapper config;
    private final SystemHookSetup systemHooks;
    private NetworkKCWrapper networkConfig;

    private Component networkMngr;
    private Component overlayMngr;
    private Component natDetection;
    private Component natTraverser;
    private Component nodeComp;

    private DecoratedAddress selfAdr = null;
    private Pair<UUID, UUID> nodeBindings = Pair.with(UUID.randomUUID(), UUID.randomUUID());

    public NodeHostComp(NodeHostInit init) {
        this.config = init.config;
        this.systemHooks = init.systemHooks;
        this.logPrefix = "<nid:" + config.system.id + "> ";
        LOG.info("{}initializing with seed:{}", logPrefix, config.system.seed);

        subscribe(handleStart, control);
        connectNetworkMngr(true);
    }

    //*************************CONTROL******************************************
    private Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
        }
    };

    @Override
    public Fault.ResolveAction handleFault(Fault fault) {
        LOG.error("{}child component failure:{}", logPrefix, fault);
        System.exit(1);
        return Fault.ResolveAction.RESOLVED;
    }

    private void connectNetworkMngr(boolean started) {
        networkMngr = create(NetworkMngrComp.class, new NetworkMngrComp.NetworkMngrInit(config.configCore, systemHooks));
        subscribe(handleNetworkMngrReady, networkMngr.getPositive(NetworkMngrPort.class));
        subscribe(handleBindPort, networkMngr.getPositive(NetworkMngrPort.class));
    }

    Handler handleNetworkMngrReady = new Handler<NetworkMngrReady>() {
        @Override
        public void handle(NetworkMngrReady event) {
            networkConfig = new NetworkKCWrapper(config.configCore);
            LOG.info("{}network manager ready on local interface:{}", logPrefix, networkConfig.localIp);

            //TODO Alex - using port + 1 due to nat emulator - since I need to comnmunicate outside before i know public address - maybe fix later
            selfAdr = DecoratedAddress.open(networkConfig.localIp, config.system.port + 1, config.system.id);
            bind(selfAdr);
        }
    };

    private void bind(DecoratedAddress adr) {
        Bind.Request req = new Bind.Request(nodeBindings.getValue0(), selfAdr, true);
        LOG.trace("{}bind request:{} adr:{}", new Object[]{logPrefix, req.id, selfAdr});
        trigger(req, networkMngr.getPositive(NetworkMngrPort.class));
    }

    Handler handleBindPort = new Handler<Bind.Response>() {
        @Override
        public void handle(Bind.Response resp) {
            selfAdr = selfAdr.changePort(resp.boundPort);
            LOG.info("{}bind response:{} adr:{}", new Object[]{logPrefix, resp.req.id, selfAdr});
            if (resp.req.id.equals(nodeBindings.getValue0())) {
                connectOverlayMngr();
                connectNatDetection();
                LOG.info("{}waiting for nat detection...", logPrefix);
            } else if (resp.req.id.equals(nodeBindings.getValue1())) {
                trigger(new SelfAddressUpdate(selfAdr), overlayMngr.getNegative(SelfAddressUpdatePort.class));
                connectNatTraverser();
                LOG.info("{} waiting for nat traverser...", logPrefix);
            }
        }
    };

    private void connectOverlayMngr() {
        overlayMngr = create(OverlayMngrComp.class, new OverlayMngrInit(config.configCore, selfAdr));
        connect(overlayMngr.getNegative(Timer.class), timer);
        connect(overlayMngr.getNegative(Network.class), networkMngr.getPositive(Network.class));

        trigger(Start.event, overlayMngr.control());
    }

    private void connectNatDetection() {
        natDetection = create(NatDetectionComp.class, new NatDetectionInit(config.configCore, systemHooks));
        connect(natDetection.getNegative(Timer.class), timer);
        connect(natDetection.getNegative(Network.class), networkMngr.getPositive(Network.class));
        connect(natDetection.getNegative(NetworkMngrPort.class), networkMngr.getPositive(NetworkMngrPort.class));
        connect(natDetection.getNegative(OverlayMngrPort.class), overlayMngr.getPositive(OverlayMngrPort.class));

        trigger(Start.event, natDetection.control());
        subscribe(handleNatReady, natDetection.getPositive(NatDetectionPort.class));
    }

    Handler handleNatReady = new Handler<NatDetected>() {
        @Override
        public void handle(NatDetected event) {
            LOG.info("{}nat detection ready", logPrefix);
            if (event.nat.type.equals(Nat.Type.OPEN)) {
                LOG.info("{}open node", logPrefix);
                selfAdr = DecoratedAddress.open(selfAdr.getIp(), config.system.port, config.system.id);
            } else if (event.nat.type.equals(Nat.Type.NAT)) {
                LOG.info("{}detected nat:{} public ip:{}",
                        new Object[]{logPrefix, event.nat.type, event.publicIp.get().getHostAddress()});
                selfAdr = new DecoratedAddress(new BasicAddress(event.publicIp.get(), config.system.port, config.system.id));
                selfAdr.addTrait(event.nat);
            } else {
                LOG.error("{}not yet handling nat result:{}", logPrefix, event.nat);
                throw new RuntimeException("not yet handling nat result:" + event.nat);
            }
            bind(selfAdr);
        }
    };

    //****************************NAT TRAVERSER STEP****************************
    private void connectNatTraverser() {
        natTraverser = create(NatTraverserComp.class, new NatTraverserInit(config.configCore, systemHooks, selfAdr));
        connect(natTraverser.getNegative(Timer.class), timer);
        connect(natTraverser.getNegative(Network.class), networkMngr.getPositive(Network.class));
        connect(natTraverser.getNegative(OverlayMngrPort.class), overlayMngr.getPositive(OverlayMngrPort.class));
        //TODO Alex connect fd

        connect(overlayMngr.getNegative(SelfAddressUpdatePort.class), natTraverser.getPositive(SelfAddressUpdatePort.class));
        subscribe(handleNatTraverserReady, natTraverser.getPositive(StatusPort.class));
        subscribe(handleSelfAddressUpdate, natTraverser.getPositive(SelfAddressUpdatePort.class));

        trigger(Start.event, natTraverser.control());
    }

    Handler handleSelfAddressUpdate = new Handler<SelfAddressUpdate>() {
        @Override
        public void handle(SelfAddressUpdate event) {
            selfAdr = event.self;
            LOG.info("{}changed self address:{}", new Object[]{logPrefix, selfAdr});
        }
    };

    Handler handleNatTraverserReady = new Handler<Ready<NatStatus>>() {
        @Override
        public void handle(Ready<NatStatus> event) {
            LOG.info("{}nat traverser ready", logPrefix);
        }
    };

    private void connectApp() {
        nodeComp = create(NodeComp.class, new NodeComp.NodeInit(config.configCore, selfAdr));
        connect(nodeComp.getNegative(Timer.class), timer);
        connect(nodeComp.getNegative(Network.class), networkMngr.getPositive(Network.class));
    }

    private void setupAppCroupier() {
        subscribe(handleAppCroupierReady, overlayMngr.getPositive(OverlayMngrPort.class));

        OMngrCroupier.ConnectRequestBuilder reqBuilder = new OMngrCroupier.ConnectRequestBuilder(UUID.randomUUID());
        reqBuilder.setIdentifiers(OverlayIds.globalCroupier, OverlayIds.appCroupier);
        reqBuilder.setupCroupier(false);
        reqBuilder.connectTo(nodeComp.getNegative(CroupierPort.class), nodeComp.getPositive(SelfViewUpdatePort.class));
        LOG.info("{}waiting for croupier app...", logPrefix);
        trigger(reqBuilder.build(), overlayMngr.getPositive(OverlayMngrPort.class));
    }

    Handler handleAppCroupierReady = new Handler<OMngrCroupier.ConnectResponse>() {
        @Override
        public void handle(OMngrCroupier.ConnectResponse resp) {
            LOG.info("{}app croupier ready", logPrefix);
            startApp();
        }
    };

    private void startApp() {
        trigger(Start.event, nodeComp.control());
    }

    public static class NodeHostInit extends Init<NodeHostComp> {

        public final NodeKCWrapper config;
        public final SystemHookSetup systemHooks;

        public NodeHostInit(KConfigCore configCore, SystemHookSetup systemHooks) {
            this.config = new NodeKCWrapper(configCore);
            this.systemHooks = systemHooks;
        }
    }
}

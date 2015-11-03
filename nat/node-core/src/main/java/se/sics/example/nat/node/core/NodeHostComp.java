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
import se.sics.nat.stun.NatDetected;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
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
    private DecoratedAddress self;

    private Component networkMngr;
    private Component overlayMngr;
    private Component natDetection;
    private Component nodeComp;

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

            DecoratedAddress self = DecoratedAddress.open(networkConfig.localIp, config.system.port, config.system.id);
            Bind.Request req = new Bind.Request(UUID.randomUUID(), self, true); 
            LOG.trace("{}bound response:{} adr:{}", new Object[]{logPrefix, req.id, 
                req.self.getBase()});
            trigger(req, networkMngr.getPositive(NetworkMngrPort.class));
        }
    };

    Handler handleBindPort = new Handler<Bind.Response>() {
        @Override
        public void handle(Bind.Response resp) {
            self = DecoratedAddress.open(networkConfig.localIp, resp.boundPort, config.system.id);
            LOG.trace("{}bound response:{} adr:{} port:{}", new Object[]{logPrefix, resp.req.id, 
                resp.req.self.getBase(), resp.boundPort});
            LOG.info("{}bound port:{}", logPrefix, resp.boundPort);
            connectOverlayMngr(false);
            connectNatDetection(false);
            LOG.info("{}waiting for nat detection...", logPrefix);
        }
    };

    private void connectOverlayMngr(boolean started) {
        overlayMngr = create(OverlayMngrComp.class, new OverlayMngrInit(config.configCore, self));
        connect(overlayMngr.getNegative(Timer.class), timer);
        connect(overlayMngr.getNegative(Network.class), networkMngr.getPositive(Network.class));

        if (!started) {
            trigger(Start.event, overlayMngr.control());
        }
    }

    private void connectNatDetection(boolean started) {
        natDetection = create(NatDetectionComp.class, new NatDetectionInit(config.configCore, systemHooks));
        connect(natDetection.getNegative(Timer.class), timer);
        connect(natDetection.getNegative(Network.class), networkMngr.getPositive(Network.class));
        connect(natDetection.getNegative(NetworkMngrPort.class), networkMngr.getPositive(NetworkMngrPort.class));
        connect(natDetection.getNegative(OverlayMngrPort.class), overlayMngr.getPositive(OverlayMngrPort.class));
        
        if(!started) {
            trigger(Start.event, natDetection.control());
        }
    }
    
    Handler handleNatReady = new Handler<NatDetected>() {
        @Override
        public void handle(NatDetected event) {
            LOG.info("{}detected nat:{}", event.nat);
        }
    };

    private void connectApp() {
        nodeComp = create(NodeComp.class, new NodeComp.NodeInit(config.configCore, self));
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

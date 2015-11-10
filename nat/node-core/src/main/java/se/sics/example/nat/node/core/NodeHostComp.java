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
import se.sics.ktoolbox.overlaymngr.OverlayMngrPort;
import se.sics.ktoolbox.overlaymngr.events.OMngrCroupier;
import se.sics.nat.manager.NatManagerComp;
import se.sics.nat.manager.NatManagerComp.NatManagerInit;
import se.sics.nat.manager.NatManagerReady;
import se.sics.nat.network.NetworkKCWrapper;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
import se.sics.p2ptoolbox.util.status.Status;
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

    private Component natMngr;
    private Component nodeComp;

    private DecoratedAddress selfAdr = null;

    public NodeHostComp(NodeHostInit init) {
        this.config = init.config;
        this.systemHooks = init.systemHooks;
        this.logPrefix = "<nid:" + config.system.id + "> ";
        LOG.info("{}initializing with seed:{}", logPrefix, config.system.seed);

        subscribe(handleStart, control);
    }

    //*************************CONTROL******************************************
    private Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            connectNatManager();
        }
    };

    @Override
    public Fault.ResolveAction handleFault(Fault fault) {
        LOG.error("{}child component failure:{}", logPrefix, fault);
        System.exit(1);
        return Fault.ResolveAction.RESOLVED;
    }
    
    private void connectNatManager() {
        natMngr = create(NatManagerComp.class, new NatManagerInit(config.configCore, systemHooks));
        connect(natMngr.getNegative(Timer.class), timer);
        subscribe(handleNatManagerReady, natMngr.getPositive(StatusPort.class));
        trigger(Start.event, natMngr.control());
    }

    Handler handleSelfAddressUpdate = new Handler<SelfAddressUpdate>() {
        @Override
        public void handle(SelfAddressUpdate event) {
            selfAdr = event.self;
            LOG.info("{}changed self address:{}", new Object[]{logPrefix, selfAdr});
        }
    };
    
    Handler handleNatManagerReady = new Handler<Status.Internal<NatManagerReady>>() {
        @Override
        public void handle(Status.Internal<NatManagerReady> ready) {
            LOG.info("{}nat manager ready", logPrefix);
            selfAdr = ready.status.selfAdr;
            connectApp();
            setupAppCroupier();
        }
    };

    private void connectApp() {
        nodeComp = create(NodeComp.class, new NodeComp.NodeInit(config.configCore, selfAdr));
        connect(nodeComp.getNegative(Timer.class), timer);
        connect(nodeComp.getNegative(Network.class), natMngr.getPositive(Network.class));
        connect(nodeComp.getNegative(SelfAddressUpdatePort.class), natMngr.getPositive(SelfAddressUpdatePort.class));
    }

    private void setupAppCroupier() {
        subscribe(handleAppCroupierReady, natMngr.getPositive(OverlayMngrPort.class));

        OMngrCroupier.ConnectRequestBuilder reqBuilder = new OMngrCroupier.ConnectRequestBuilder(UUID.randomUUID());
        reqBuilder.setIdentifiers(config.globalCroupier.array(), config.pingService.array());
        reqBuilder.setupCroupier(false);
        reqBuilder.connectTo(nodeComp.getNegative(CroupierPort.class), nodeComp.getPositive(SelfViewUpdatePort.class));
        LOG.info("{}waiting for croupier app...", logPrefix);
        trigger(reqBuilder.build(), natMngr.getPositive(OverlayMngrPort.class));
    }

    Handler handleAppCroupierReady = new Handler<OMngrCroupier.ConnectResponse>() {
        @Override
        public void handle(OMngrCroupier.ConnectResponse resp) {
            LOG.info("{}app croupier ready", logPrefix);
            trigger(Start.event, nodeComp.control());
        }
    };

    public static class NodeHostInit extends Init<NodeHostComp> {

        public final NodeKCWrapper config;
        public final SystemHookSetup systemHooks;

        public NodeHostInit(KConfigCore configCore, SystemHookSetup systemHooks) {
            this.config = new NodeKCWrapper(configCore);
            this.systemHooks = systemHooks;
        }
    }
}

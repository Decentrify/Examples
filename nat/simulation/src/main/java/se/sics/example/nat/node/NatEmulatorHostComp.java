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
package se.sics.example.nat.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.networkmngr.NetworkMngrHooks;
import se.sics.ktoolbox.networkmngr.hooks.NetworkHookFactory;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.config.impl.SystemKCWrapper;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatEmulatorHostComp extends ComponentDefinition {

    private Logger LOG = LoggerFactory.getLogger(NatEmulatorHostComp.class);
    private String logPrefix = "";

    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    
    private final SystemKCWrapper config;
    private final SystemHookSetup systemHooks;
    private Component host;
    private Component natEmulator;
    
    public NatEmulatorHostComp(NatEmulatorHostInit init) {
        config = init.config;
        systemHooks = init.systemHooks;
        logPrefix = "<" + config.id + "> ";
        LOG.info("{}initiating", logPrefix);
        subscribe(handleStart, control);

        systemHooks.register(NetworkMngrHooks.RequiredHooks.NETWORK.hookName, NetworkHookFactory.getNetworkEmulator(network));
        host = create(init.hostClass, init.hostInit);
        connect(host.getNegative(Timer.class), timer);
    }
    
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
        }
    };
    
    public static class NatEmulatorHostInit extends Init<NatEmulatorHostComp> {
        public final SystemKCWrapper config;
        public final SystemHookSetup systemHooks;
        public final Class hostClass;
        public final Init hostInit;
        
        public NatEmulatorHostInit(KConfigCore config, SystemHookSetup systemHooks, Class hostClass, Init hostInit) {
            this.config = new SystemKCWrapper(config);
            this.systemHooks = systemHooks;
            this.hostClass = hostClass;
            this.hostInit = hostInit;
        }
    }
}

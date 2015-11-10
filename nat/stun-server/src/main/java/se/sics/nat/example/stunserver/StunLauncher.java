/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * KompicsToolbox is free software; you can redistribute it and/or
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
package se.sics.nat.example.stunserver;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.ConfigFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Init;
import se.sics.kompics.Kompics;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.timer.java.JavaTimer;
import se.sics.ktoolbox.ipsolver.hooks.IpSolverHookFactory;
import se.sics.ktoolbox.overlaymngr.OMngrSerializerSetup;
import se.sics.nat.hooks.BaseHooks;
import se.sics.nat.stun.StunSerializerSetup;
import se.sics.nat.stun.server.StunServerHostComp;
import se.sics.nat.stun.server.StunServerHostComp.StunServerHostInit;
import se.sics.p2ptoolbox.croupier.CroupierSerializerSetup;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.hooks.NetworkHookFactory;
import se.sics.p2ptoolbox.util.network.hooks.PortBindingHookFactory;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;
import se.sics.p2ptoolbox.util.traits.AcceptedTraits;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunLauncher extends ComponentDefinition {

    private final Component timer;
    private final Component stunHost;

    public StunLauncher() {
        KConfigCore config = new KConfigCore(ConfigFactory.load());
        SystemHookSetup systemHooks = new SystemHookSetup();
        systemSetup(config, systemHooks);

        timer = create(JavaTimer.class, Init.NONE);
        stunHost = create(StunServerHostComp.class, new StunServerHostInit(config, systemHooks));
        connect(stunHost.getNegative(Timer.class), timer.getPositive(Timer.class));
    }

    private void systemSetup(KConfigCore config, SystemHookSetup systemHooks) {
        //address setup
        ImmutableMap acceptedTraits = ImmutableMap.of(NatedTrait.class, 0);
        DecoratedAddress.setAcceptedTraits(new AcceptedTraits(acceptedTraits));

        //serializers setup
        int serializerId = 128;
        serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
        serializerId = CroupierSerializerSetup.registerSerializers(serializerId);
        serializerId = OMngrSerializerSetup.registerSerializers(serializerId);
        serializerId = StunSerializerSetup.registerSerializers(serializerId);

        OMngrSerializerSetup.checkSetup();
        StunSerializerSetup.checkSetup();
        
        if (serializerId > 255) {
            throw new RuntimeException("switch to bigger serializerIds, last serializerId:" + serializerId);
        }

        //hooks setup
        systemHooks.register(BaseHooks.RequiredHooks.IP_SOLVER.hookName, IpSolverHookFactory.getIpSolver());
        systemHooks.register(BaseHooks.RequiredHooks.PORT_BINDING.hookName, PortBindingHookFactory.getPortBinder());
        systemHooks.register(BaseHooks.RequiredHooks.NETWORK.hookName, NetworkHookFactory.getNettyNetwork());
    }

    public static void main(String[] args) {
        start();
        try {
            Kompics.waitForTermination();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    public static void start() {
        if (Kompics.isOn()) {
            Kompics.shutdown();
        }
        Kompics.createAndStart(StunLauncher.class, Runtime.getRuntime().availableProcessors(), 20); // Yes 20 is totally arbitrary
    }

    public static void stop() {
        Kompics.shutdown();
    }
}

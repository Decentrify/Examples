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
package se.sics.example.nat;

import com.typesafe.config.ConfigFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import org.javatuples.Pair;
import se.sics.nat.emulator.NatEmulatorComp.NatEmulatorInit;
import se.sics.p2ptoolbox.util.helper.SystemConfigBuilder;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ScenarioSetup {

    public static final long baseSeed = 1234;
    public static final Pair<Integer, Integer> stunServerPorts = Pair.with(31000, 31001);
    public static final Pair<Integer, Integer> stunClientPorts = Pair.with(32000, 32001); 
    public static final int appPort = 30000;
    public static final NatedTrait[] nats = new NatedTrait[2];
    public static final DecoratedAddress globalCroupierBoot;

    public static enum ScenarioNat {

        OP("193.0.0.", "193.0.0."), EI_PP_EI("193.1.0.", "192.168.0.");
        String publicIp;
        String privateIp;

        ScenarioNat(String publicIp, String privateIp) {
            this.publicIp = publicIp;
            this.privateIp = privateIp;
        }
    }

    static {
        nats[0] = NatedTrait.open();
        nats[1] = NatedTrait.nated(Nat.MappingPolicy.ENDPOINT_INDEPENDENT, Nat.AllocationPolicy.PORT_PRESERVATION, 0,
                Nat.FilteringPolicy.ENDPOINT_INDEPENDENT, 10000, new ArrayList<DecoratedAddress>());
        globalCroupierBoot = DecoratedAddress.open(getLocalIp(0, ScenarioNat.OP), appPort, 0);
    }

    public static NatEmulatorInit getNatEmulator(int natId, ScenarioNat natType) {
        long natSeed = baseSeed + natId;
        NatedTrait nat = nats[natType.ordinal()];
        InetAddress natIp = null;
        try {
            natIp = InetAddress.getByName(natType.publicIp + natId);
        } catch (UnknownHostException ex) {
            System.out.println("scenario setup ip exception");
            throw new RuntimeException("scenario setup", ex);
        }
        
        return new NatEmulatorInit(natSeed, nat, natIp, natId);
    }
    
    public static SystemConfigBuilder getSystemConfig(int nodeId, ScenarioNat natType) {
        long nodeSeed = baseSeed + nodeId;
        
        InetAddress nodeIp = getLocalIp(nodeId, natType);
        return new SystemConfigBuilder(nodeSeed, nodeIp, appPort, nodeId, ConfigFactory.load());
    }
    
    public static InetAddress getLocalIp(int nodeId, ScenarioNat natType) {
        InetAddress nodeIp = null;
        try {
            nodeIp = InetAddress.getByName(natType.privateIp + nodeId);
            return nodeIp;
        } catch (UnknownHostException ex) {
            System.out.println("scenario setup ip exception");
            throw new RuntimeException("scenario setup", ex);
        }
    }
}

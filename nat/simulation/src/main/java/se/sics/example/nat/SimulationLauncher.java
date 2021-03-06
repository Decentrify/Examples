/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * NatTraverser is free software; you can redistribute it and/or
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

import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;
import se.sics.p2ptoolbox.simulator.run.LauncherComp;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class SimulationLauncher {

    public static void main(String[] args) {
        long seed = 1234;

        SimulationScenario simpleBootScenario = ScenarioGen.ping();
        simpleBootScenario.setSeed(seed);
        try {
            simpleBootScenario.simulate(LauncherComp.class);
        } catch (Exception ex) {
            printException(ex);
        }
    }

    private static void printException(Throwable t) {
        System.out.println("*************");
        System.out.println(t);
        System.out.println(t.getMessage());
        for (StackTraceElement ste : t.getStackTrace()) {
            System.out.println(ste);
        }
        if (t.getCause() != null) {
            printException(t.getCause());
        }
    }
}

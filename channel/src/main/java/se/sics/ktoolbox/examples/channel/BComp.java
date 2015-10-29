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
package se.sics.ktoolbox.examples.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class BComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(BComp.class);
    private String logPrefix = "";

    Positive<XYPort> xyPort = requires(XYPort.class);

    private final int id;

    public BComp(BInit init) {
        this.id = init.id;
        this.logPrefix = id + " ";
        LOG.info("{}initiating", logPrefix);
        subscribe(handleStart, control);
        subscribe(handleXMsg, xyPort);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
        }
    };

    Handler handleXMsg = new Handler<XEvent>() {
        @Override
        public void handle(XEvent event) {
            LOG.info("{}received x:{}", logPrefix, event.id);
            LOG.info("{}sending y:{}", logPrefix, event.id + 1);
            trigger(new YEvent(event.id + 1), xyPort);
            
        }
    };

    public static class BInit extends Init<BComp> {

        public final int id;

        public BInit(int id) {
            this.id = id;
        }
    }
}

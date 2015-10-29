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

import se.sics.kompics.Channel;
import se.sics.kompics.ChannelCore;
import se.sics.kompics.ChannelCoreImpl;
import se.sics.kompics.ChannelFilter;
import se.sics.kompics.Negative;
import se.sics.kompics.PortCore;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ComponentHelper {

    public static <P extends PortType> Channel<P> connect(Positive<P> positive, ChannelFilter<?, ?> positiveFilter, 
            Negative<P> negative, ChannelFilter<?, ?> negativeFilter) {
        PortCore<P> positivePort = (PortCore<P>) positive;
        PortCore<P> negativePort = (PortCore<P>) negative;
        if (!positivePort.getPortType().hasPositive(positiveFilter.getEventType())) {
            throw new RuntimeException("Port type " + positivePort.getPortType()
                    + " has no positive " + positiveFilter.getEventType());
        }
        if (!negativePort.getPortType().hasNegative(negativeFilter.getEventType())) {
            throw new RuntimeException("Port type " + negativePort.getPortType()
                    + " has no negative " + negativeFilter.getEventType());
        }

        ChannelCore<P> channel = new ChannelCoreImpl<P>(positivePort, negativePort, negativePort.getPortType());
        positivePort.addChannel(channel, positiveFilter);
        negativePort.addChannel(channel, negativeFilter);
        return channel;
    }
    
   public static <P extends PortType> Channel<P> connect(Negative<P> negative, ChannelFilter<?, ?> negativeFilter, 
            Positive<P> positive, ChannelFilter<?, ?> positiveFilter) {
       return connect(positive, positiveFilter, negative, negativeFilter);
   }
}

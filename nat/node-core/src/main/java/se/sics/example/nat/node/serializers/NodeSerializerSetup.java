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
package se.sics.example.nat.node.serializers;

import se.sics.example.nat.node.msg.NodeMsg;
import se.sics.example.nat.node.util.NodeView;
import se.sics.kompics.network.netty.serialization.Serializers;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NodeSerializerSetup {

    public static final int serializerIds = 3;

    public static enum NodeSerializers {

        Ping(NodeMsg.Ping.class, "nodePingSerializer"),
        Pong(NodeMsg.Pong.class, "nodePongSerializer"),
        NodeView(NodeView.class, "nodeViewSerializer");

        public final Class serializedClass;
        public final String serializerName;

        NodeSerializers(Class serializedClass, String serializerName) {
            this.serializedClass = serializedClass;
            this.serializerName = serializerName;
        }
    }

    public static void checkSetup() {
    }

    public static int registerSerializers(int startingId) {
        if (startingId < 128) {
            throw new RuntimeException("start your serializer ids at 128");
        }
        int currentId = startingId;

        NodeMsgSerializer.Ping nodeRequestSerializer = new NodeMsgSerializer.Ping(currentId++);
        Serializers.register(nodeRequestSerializer, NodeSerializers.Ping.serializerName);
        Serializers.register(NodeSerializers.Ping.serializedClass, NodeSerializers.Ping.serializerName);

        NodeMsgSerializer.Pong nodeResponseSerializer = new NodeMsgSerializer.Pong(currentId++);
        Serializers.register(nodeResponseSerializer, NodeSerializers.Pong.serializerName);
        Serializers.register(NodeSerializers.Pong.serializedClass, NodeSerializers.Pong.serializerName);
        
        NodeViewSerializer nodeViewSerializer = new NodeViewSerializer(currentId++);
        Serializers.register(nodeViewSerializer, NodeSerializers.NodeView.serializerName);
        Serializers.register(NodeSerializers.NodeView.serializedClass, NodeSerializers.NodeView.serializerName);

        assert startingId + serializerIds == currentId;

        return currentId;
    }
}

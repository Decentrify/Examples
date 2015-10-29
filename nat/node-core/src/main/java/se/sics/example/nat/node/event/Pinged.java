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

package se.sics.example.nat.node.event;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import se.sics.kompics.Direct;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class Pinged {
    public static class Request extends Direct.Request<Response> {
        public final UUID id;
        public Request(UUID id) {
            super();
            this.id = id;
        }
        
        public Response answer(DecoratedAddress self, List<DecoratedAddress> pinged) {
            return new Response(id, self, pinged);
        }
    }
    
    public static class Response implements Direct.Response {
        public final UUID id;
        public final DecoratedAddress self;
        public final List<DecoratedAddress> pinged;
        
        public Response(UUID id, DecoratedAddress self, List<DecoratedAddress> pinged) {
            this.id = id;
            this.self = self;
            this.pinged = pinged;
        }
    }
}

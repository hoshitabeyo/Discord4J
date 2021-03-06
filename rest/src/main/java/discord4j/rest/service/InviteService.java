/*
 * This file is part of Discord4J.
 *
 * Discord4J is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Discord4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Discord4J.  If not, see <http://www.gnu.org/licenses/>.
 */
package discord4j.rest.service;

import discord4j.rest.json.response.InviteResponse;
import discord4j.rest.request.Router;
import discord4j.rest.route.Routes;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;

public class InviteService extends RestService {

    public InviteService(Router router) {
        super(router);
    }

    public Mono<InviteResponse> getInvite(String inviteCode) {
        return Routes.INVITE_GET.newRequest(inviteCode)
                .exchange(getRouter());
    }

    public Mono<InviteResponse> deleteInvite(String inviteCode, @Nullable String reason) {
        return Routes.INVITE_DELETE.newRequest(inviteCode)
                .optionalHeader("X-Audit-Log-Reason", reason)
                .exchange(getRouter());
    }
}

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
package discord4j.core.spec;

import discord4j.common.json.OverwriteEntity;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Channel;
import discord4j.rest.json.request.ChannelCreateRequest;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

public class CategoryCreateSpec implements AuditSpec<ChannelCreateRequest> {

    private final ChannelCreateRequest.Builder requestBuilder = ChannelCreateRequest.builder()
            .type(Channel.Type.GUILD_CATEGORY.getValue());
    @Nullable
    private String reason;

    public CategoryCreateSpec setName(String name) {
        requestBuilder.name(name);
        return this;
    }

    public CategoryCreateSpec setPosition(int position) {
        requestBuilder.setPosition(position);
        return this;
    }

    public CategoryCreateSpec setPermissionOverwrites(Set<? extends PermissionOverwrite> permissionOverwrites) {
        OverwriteEntity[] raw = permissionOverwrites.stream()
                .map(o -> new OverwriteEntity(o.getTargetId().asLong(), o.getType().getValue(), o.getAllowed().getRawValue(), o.getDenied().getRawValue()))
                .toArray(OverwriteEntity[]::new);

        requestBuilder.permissionOverwrites(raw);
        return this;
    }

    @Override
    public CategoryCreateSpec setReason(@Nullable final String reason) {
        this.reason = reason;
        return this;
    }

    @Override
    @Nullable
    public String getReason() {
        return reason;
    }

    @Override
    public ChannelCreateRequest asRequest() {
        return requestBuilder.build();
    }
}

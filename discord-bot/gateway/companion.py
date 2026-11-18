"""Optional Discord Gateway features; slash commands remain with the Worker.

Uses the SAME Discord application/token. Does not sync or register commands.
Needs Server Members and Message Content intents enabled in the developer portal.
"""
import logging
import os
from urllib.parse import urlsplit

import aiohttp
import discord
from discord.ext import tasks
from dotenv import load_dotenv

load_dotenv()
LOG = logging.getLogger("mandomc.gateway")


class Companion(discord.Client):
    def __init__(self):
        intents = discord.Intents.default()
        intents.members = True
        intents.message_content = True
        super().__init__(intents=intents, allowed_mentions=discord.AllowedMentions.none())
        self.guild_id = int(os.environ["DISCORD_GUILD_ID"])
        self.citizen_role = int(os.environ.get("CITIZEN_ROLE_ID") or "0")
        self.category_id = int(os.environ["TICKET_CATEGORY_ID"])
        self.staff_roles = {int(role) for role in os.environ.get("STAFF_ROLE_IDS", "").split(",") if role.strip()}
        self.origin = os.environ["WORKER_URL"].rstrip("/")
        url = urlsplit(self.origin)
        if url.scheme != "https" or not url.hostname or url.path or url.query or url.fragment or url.username:
            raise ValueError("WORKER_URL must be an HTTPS origin")
        self.secret = os.environ["GATEWAY_SECRET"]
        if len(self.secret) < 32:
            raise ValueError("GATEWAY_SECRET must have at least 32 characters")
        self.session = None

    async def setup_hook(self):
        self.session = aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=20))
        self.presence.start()

    async def bridge(self, path, body=None):
        async with self.session.request("GET" if body is None else "POST", self.origin + path,
                                        headers={"Authorization": "Bearer " + self.secret},
                                        json=body, allow_redirects=False) as response:
            if response.status != 200:
                raise RuntimeError(f"Worker returned {response.status}")
            return await response.json()

    async def on_ready(self):
        LOG.info("Gateway connected; Worker retains slash-command ownership")

    async def on_member_join(self, member):
        if member.guild.id != self.guild_id or not self.citizen_role:
            return
        role = member.guild.get_role(self.citizen_role)
        if role:
            try:
                await member.add_roles(role, reason="MandoMC citizen on join")
            except discord.HTTPException:
                LOG.warning("Citizen role assignment failed; check role hierarchy")

    @tasks.loop(seconds=30)
    async def presence(self):
        try:
            data = await self.bridge("/gateway/status")
            count = data.get("online", 0)
            text = "Server offline" if data["stale"] else f"{count} {'player' if count == 1 else 'players'} online"
            await self.change_presence(activity=discord.Activity(type=discord.ActivityType.watching, name=text))
        except (aiohttp.ClientError, RuntimeError):
            LOG.warning("Status refresh unavailable; retrying")

    @presence.before_loop
    async def before_presence(self):
        await self.wait_until_ready()

    async def on_message(self, source):
        if source.author.bot:
            return
        try:
            if isinstance(source.channel, discord.DMChannel):
                guild = self.get_guild(self.guild_id)
                if not guild:
                    return
                try:
                    await guild.fetch_member(source.author.id)
                except discord.NotFound:
                    await source.channel.send("Join the MandoMC Discord server before opening a ticket.")
                    return
                row = await self.bridge("/gateway/ticket", {"user": str(source.author.id)})
                destination = await self.fetch_channel(int(row["channel_id"]))
            elif source.guild and source.guild.id == self.guild_id and isinstance(source.channel, discord.TextChannel):
                if source.channel.category_id != self.category_id:
                    return
                if not source.author.guild_permissions.administrator and not any(role.id in self.staff_roles for role in source.author.roles):
                    return
                row = await self.bridge("/gateway/ticket", {"channel": str(source.channel.id)})
                if not row.get("discord_id"):
                    return
                destination = await self.fetch_user(int(row["discord_id"]))
            else:
                return
            content = source.content
            if source.attachments:
                content += "\n" + "\n".join(attachment.url for attachment in source.attachments)
            content = content or "[No text content]"
            for offset in range(0, len(content), 4000):
                embed = discord.Embed(description=content[offset:offset + 4000], color=0x3498DB)
                embed.set_author(name=source.author.display_name, icon_url=source.author.display_avatar.url)
                await destination.send(embed=embed)
        except (aiohttp.ClientError, discord.HTTPException, RuntimeError):
            LOG.warning("Ticket relay failed; source message retained")
            try:
                await source.channel.send("That message could not be relayed. Please try again or use your ticket channel.")
            except discord.HTTPException:
                pass

    async def close(self):
        self.presence.cancel()
        if self.session:
            await self.session.close()
        await super().close()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    Companion().run(os.environ["DISCORD_TOKEN"], log_handler=None)

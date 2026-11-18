import os
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock, patch

import discord
from companion import Companion

ENV = {"DISCORD_GUILD_ID": "111111111111111111", "TICKET_CATEGORY_ID": "666666666666666666",
       "CITIZEN_ROLE_ID": "444444444444444444", "STAFF_ROLE_IDS": "888888888888888888",
       "WORKER_URL": "https://bot.example", "GATEWAY_SECRET": "x" * 64}


class CompanionTest(unittest.IsolatedAsyncioTestCase):
    def client(self):
        with patch.dict(os.environ, ENV, clear=True):
            return Companion()

    def source(self, channel):
        return SimpleNamespace(author=SimpleNamespace(bot=False, id=222222222222222222,
                               display_name="Mando", display_avatar=SimpleNamespace(url="https://example.com/avatar.png")),
                               channel=channel, content="help", attachments=[], guild=None)

    async def test_dm_from_guild_member_relayed_to_durable_ticket(self):
        client = self.client()
        guild = MagicMock()
        guild.fetch_member = AsyncMock()
        client.get_guild = MagicMock(return_value=guild)
        client.bridge = AsyncMock(return_value={"channel_id": "777777777777777777"})
        destination = SimpleNamespace(send=AsyncMock())
        client.fetch_channel = AsyncMock(return_value=destination)
        await client.on_message(self.source(MagicMock(spec=discord.DMChannel)))
        client.bridge.assert_awaited_once_with("/gateway/ticket", {"user": "222222222222222222"})
        self.assertEqual(destination.send.await_args.kwargs["embed"].description, "help")

    async def test_non_member_cannot_open_dm_ticket(self):
        client = self.client()
        response = MagicMock(status=404, reason="Not Found")
        guild = SimpleNamespace(fetch_member=AsyncMock(side_effect=discord.NotFound(response, "missing")))
        client.get_guild = MagicMock(return_value=guild)
        client.bridge = AsyncMock()
        channel = MagicMock(spec=discord.DMChannel)
        channel.send = AsyncMock()
        await client.on_message(self.source(channel))
        client.bridge.assert_not_awaited()
        channel.send.assert_awaited_once()

    async def test_citizen_role_uses_configured_id_and_guild(self):
        client = self.client()
        role = object()
        member = SimpleNamespace(guild=SimpleNamespace(id=client.guild_id, get_role=MagicMock(return_value=role)), add_roles=AsyncMock())
        await client.on_member_join(member)
        member.add_roles.assert_awaited_once_with(role, reason="MandoMC citizen on join")
        member.guild.id = 9
        await client.on_member_join(member)
        self.assertEqual(member.add_roles.await_count, 1)

    async def test_bot_messages_are_never_relayed(self):
        client = self.client()
        source = self.source(MagicMock(spec=discord.DMChannel))
        source.author.bot = True
        client.bridge = AsyncMock()
        await client.on_message(source)
        client.bridge.assert_not_awaited()

    async def test_long_content_and_attachment_urls_are_preserved(self):
        client = self.client()
        client.get_guild = MagicMock(return_value=SimpleNamespace(fetch_member=AsyncMock()))
        client.bridge = AsyncMock(return_value={"channel_id": "777777777777777777"})
        destination = SimpleNamespace(send=AsyncMock())
        client.fetch_channel = AsyncMock(return_value=destination)
        source = self.source(MagicMock(spec=discord.DMChannel))
        source.content = "x" * 6000
        source.attachments = [SimpleNamespace(url="https://example.com/file.png")]
        await client.on_message(source)
        forwarded = "".join(call.kwargs["embed"].description for call in destination.send.await_args_list)
        self.assertEqual(forwarded, source.content + "\nhttps://example.com/file.png")

    async def test_status_uses_worker_snapshot_and_offline_indicator(self):
        client = self.client()
        client.bridge = AsyncMock(return_value={"stale": False, "online": 1})
        client.change_presence = AsyncMock()
        await Companion.presence.coro(client)
        self.assertEqual(client.change_presence.await_args.kwargs["activity"].name, "1 player online")
        client.bridge.return_value = {"stale": True}
        await Companion.presence.coro(client)
        self.assertEqual(client.change_presence.await_args.kwargs["activity"].name, "Server offline")


if __name__ == "__main__":
    unittest.main()

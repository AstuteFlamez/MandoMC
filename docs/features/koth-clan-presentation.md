# Feature: KOTH clan presentation
Status: implementation, verified and awaiting rollout
Branch: bilal/koth-clan-presentation

## Player experience
Capture and win notices name the current clan. Its members see the name in green, unrelated viewers see blue, and the current raid attacker sees the defending clan in red. Solo holders appear by their rich player display name. The progress bossbar follows the same policy.

## Rules
Reuse `TerritoryIdentity.read` and the existing `ClanService` state, including the directional active-raid rule. Warpoint eligibility alone does not turn a name red. Keep all capture, eligibility, participant, payout, key-save and timer rules unchanged. Preserve broadcast permissions, cancellation and listener audience edits. Keep the existing yellow EVENT brand and body/channel styling; insert relationship components after chat styling so name colors survive.

## Smallest playable version
For `CanyonClan`, the same `ᴍᴀɴᴅᴏᴍᴄ » CanyonClan is capturing the canyon!` message simultaneously shows a green clan name to its members, a red name to its active raiding attacker and a blue name to a third clan. Progress reads `KOTH — CanyonClan 179s remaining` with only the name receiving relationship color. Reward text still names the single eligible key recipient.

## Ownership and integrations
KOTH owns presentation and per-viewer transient bars; ClanService owns membership and raid state; TerritoryIdentity owns relationship classification. The deployed ChatStyle owns chat presentation. Paper permission subscriptions and BroadcastMessageEvent own the broadcast audience. The existing crate service owns key delivery. Event menus/countdown titles/actionbars/sidebar carry no capturing-clan label and need no changes.

## Parameters
No new configuration or migrations. Existing `events/koth.yml` messages accept the current `player` and `clan` placeholders as rich contestant components. Capturing-clan wording is already name based in the deployed configuration. Existing operator translations remain intact.

## Verification
Full source build: 221 tests passed, supplying the already deployed ChatStyle/ChatCategory API classes to this older source worktree's compile output. The narrow overlay builder compiles directly against its supplied baseline and verifies six component/relationship tests and unchanged public KOTH API. The standalone disposable Paper probe passes 77 actual lifecycle/component/audience assertions, including the real KOTH crate payout and single save, without installing fixtures on live. It reads a copy of the existing deployed KOTH crate definition into its isolated fixture.

## Pause note
Two-class overlay is ready on the latest released herd baseline. The builder pins the expected KOTH implementation and preserves all unrelated JAR bytes. Shared runtime and resource pack remain untouched. Next action: coordinator-assigned backed-up deployment window.

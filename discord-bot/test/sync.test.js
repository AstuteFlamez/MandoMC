import {test} from 'node:test';
import assert from 'node:assert/strict';
import {reconcile, rankMapping} from '../src/sync.js';
const mapping = {warrior: '333333333333333333', marshal: '444444444444444444'};
const state = target => ({...reconcile(target, [], null, mapping), pending: false, discordApplied: true});

test('first link uses Minecraft, including intentional demotion', () => {
  assert.deepEqual(reconcile(['default', 'warrior'], ['marshal'], null, mapping).target, ['warrior']);
  assert.deepEqual(reconcile(['default'], ['warrior'], null, mapping).target, []);
});
test('Minecraft grants and removals propagate', () => {
  assert.deepEqual(reconcile(['warrior'], [], state([]), mapping).target, ['warrior']);
  assert.deepEqual(reconcile([], ['warrior'], state(['warrior']), mapping).target, []);
});
test('Discord grants and removals propagate', () => {
  assert.deepEqual(reconcile([], ['warrior'], state([]), mapping).target, ['warrior']);
  assert.deepEqual(reconcile(['warrior'], [], state(['warrior']), mapping).target, []);
});
test('steady state does not bounce or infer inheritance', () => {
  assert.deepEqual(reconcile(['warrior'], ['warrior'], state(['warrior']), mapping).target, ['warrior']);
  assert.deepEqual(reconcile(['builder'], [], state([]), mapping).target, []);
});
test('pending Minecraft write is replayed after lost response', () => {
  const pending = {...state(['warrior']), game: [], pending: true};
  assert.deepEqual(reconcile([], ['warrior'], pending, mapping).target, ['warrior']);
  const ack = reconcile(['warrior'], ['warrior'], pending, mapping);
  assert.deepEqual(reconcile(['warrior'], [], {...ack, pending: false}, mapping).target, []);
});
test('partial Discord write cannot become a Minecraft demotion', () => {
  const failed = {...state(['warrior', 'marshal']), pending: false, discordApplied: false};
  assert.deepEqual(reconcile(['warrior', 'marshal'], ['warrior'], failed, mapping).target, ['marshal', 'warrior']);
  assert.deepEqual(reconcile(['marshal'], ['warrior'], failed, mapping).target, ['marshal']);
});
test('mapping change resets baseline from Minecraft', () => {
  assert.deepEqual(reconcile(['warrior'], ['marshal'], state(['marshal']), {warrior: mapping.warrior}).target, ['warrior']);
});
test('mapping rejects default, duplicate roles, non-ID values and oversized maps', () => {
  for (const map of [{default: mapping.warrior}, {warrior: 'Moderator'}, {warrior: mapping.warrior, marshal: mapping.warrior}, []]) {
    assert.throws(() => rankMapping(map));
  }
  assert.deepEqual(rankMapping('{}'), {});
  assert.deepEqual(rankMapping(JSON.stringify(mapping)), mapping);
  assert.throws(() => rankMapping(Object.fromEntries(Array.from({length: 9}, (_, i) => [`rank${i}`, `${333333333333333330n + BigInt(i)}`]))));
});

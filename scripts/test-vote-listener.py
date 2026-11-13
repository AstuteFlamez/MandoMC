#!/usr/bin/env python3
"""Send one encrypted V1 vote to a disposable loopback fixture, never production."""
import argparse
import base64
from pathlib import Path
import socket
import time
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import padding

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--runtime', type=Path, required=True)
parser.add_argument('--port', type=int, default=25884)
args = parser.parse_args()
runtime = args.runtime.resolve()
if not (runtime / 'plugins/VotingProbe.jar').exists():
    parser.error('A disposable VotingProbe runtime is required')
name = (runtime / 'voting-probe-player.txt').read_text().strip()
public = serialization.load_der_public_key(base64.b64decode(
    (runtime / 'plugins/Votifier/rsa/public.key').read_bytes()))
message = f'VOTE\nMinecraft-MP.com\n{name}\n127.0.0.1\n{int(time.time())}\n'.encode()
with socket.create_connection(('127.0.0.1', args.port), timeout=10) as connection:
    greeting = connection.recv(1024)
    if not greeting.startswith(b'VOTIFIER'):
        raise ValueError('Unexpected listener greeting')
    connection.sendall(public.encrypt(message, padding.PKCS1v15()))
print('Sent encrypted vote to disposable loopback listener; check VOTING-PASSED.txt')

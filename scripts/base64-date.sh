#!/usr/bin/env bash

date -u +"%Y-%m-%dT%H:%M:%SZ" | tr -d '\n' | base64 | tr -d '\n'


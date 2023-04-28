#!/usr/bin/env sh

FILE="$1"

echo Total: "$(wc -l "$FILE")"

jq ".result.Right.value.status" "$FILE" | sort | uniq -c

echo Timeout
jq ".result.Left.value.Timeout | objects" "$FILE" | wc -l

echo Failed
jq ".result.Left.value.Failed | objects" "$FILE" | wc -l

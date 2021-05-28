#!/bin/bash

echo updating
git pull;

echo
echo compile
echo

./gradlew build

echo
echo generating
echo

java -classpath build/classes/java/main eu.cqse.break_roulette.BreakRoulette

echo
echo Now push to persist the new pairs
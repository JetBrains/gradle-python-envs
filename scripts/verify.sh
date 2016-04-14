#!/bin/bash

set -e

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd $DIR/..

export JAVA_HOME=$JAVA_1_7_HOME
./gradlew check

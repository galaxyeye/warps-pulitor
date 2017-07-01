#!/usr/bin/env bash
#
#/**
# * Copyright 2007 The Apache Software Foundation
# *
# * Licensed to the Apache Software Foundation (ASF) under one
# * or more contributor license agreements.  See the NOTICE file
# * distributed with this work for additional information
# * regarding copyright ownership.  The ASF licenses this file
# * to you under the Apache License, Version 2.0 (the
# * "License"); you may not use this file except in compliance
# * with the License.  You may obtain a copy of the License at
# *
# *     http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# */

# included in all the hbase scripts with source command
# should not be executable directly
# also should not be passed any arguments, since we need original $*
# Modelled after $HADOOP_HOME/bin/hadoop-env.sh.

# resolve links - "${BASH_SOURCE-$0}" may be a softlink
this="${BASH_SOURCE-$0}"
while [ -h "$this" ]; do
  ls=`ls -ld "$this"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '.*/.*' > /dev/null; then
    this="$link"
  else
    this=`dirname "$this"`/"$link"
  fi
done

# convert relative path to absolute path
bin=`dirname "$this"`
script=`basename "$this"`
bin=`cd "$bin">/dev/null; pwd`
this="$bin/$script"

# the root of the pulsar installation
if [ "$PULITOR_HOME" = "" ]; then
  PULITOR_HOME=`dirname "$this"`/..
  PULITOR_HOME=`cd "$PULITOR_HOME">/dev/null; pwd`
  export PULITOR_HOME=$PULITOR_HOME
fi

# Runtime Mode
if [ -f "${PULITOR_HOME}"/pulitor-*-job.jar ]; then
  export PULITOR_RUNTIME_MODE="CLUSTER_JOB"
elif [ -f "$PULITOR_HOME/pom.xml" ]; then
  export PULITOR_RUNTIME_MODE="LOCAL_DEVELOPMENT"
fi

if [ "$PULITOR_CONF_DIR" = "" ]; then
  export PULITOR_CONF_DIR="$PULITOR_HOME/conf"
fi

# get log directory
if [ "$PULITOR_LOG_DIR" = "" ]; then
  export PULITOR_LOG_DIR="$PULITOR_HOME/logs"
fi
mkdir -p "$PULITOR_LOG_DIR"

if [ "$PULITOR_NICENESS" = "" ]; then
    export PULITOR_NICENESS=0
fi

if [ "$PULITOR_IDENT_STRING" = "" ]; then
  export PULITOR_IDENT_STRING="$USER"
fi

# get tmp directory
if [ "$PULITOR_TMP_DIR" = "" ]; then
  export PULITOR_TMP_DIR="/tmp/pulitor-$USER"
fi
mkdir -p "$PULITOR_TMP_DIR"

if [ "$PULITOR_PID_DIR"="" ]; then
  export PULITOR_PID_DIR="$PULITOR_TMP_DIR"
fi

# Source the hbase-env.sh.  Will have JAVA_HOME defined
if [ -z "$PULITOR_ENV_INIT" ]; then
  [ -f "${PULITOR_CONF_DIR}/pulitor-env.sh" ] && . "${PULITOR_CONF_DIR}/pulitor-env.sh"
  [ -f "$HOME/.pulitor/pulitor-env.sh" ] && . $HOME/.pulitor/pulitor-env.sh

  export PULITOR_ENV_INIT="true"
fi

# Newer versions of glibc use an arena memory allocator that causes virtual
# memory usage to explode. Tune the variable down to prevent vmem explosion.
export MALLOC_ARENA_MAX=${MALLOC_ARENA_MAX:-4}

# Now having JAVA_HOME defined is required 
if [ -z "$JAVA_HOME" ]; then
    cat 1>&2 <<EOF
+======================================================================+
|      Error: JAVA_HOME is not set and Java could not be found         |
+======================================================================+
EOF
    exit 1
fi

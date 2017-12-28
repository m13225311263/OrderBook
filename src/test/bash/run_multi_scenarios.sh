#!/bin/bash

SCRIPT_START_DIR=`pwd`
DIRNAMECMD="/usr/bin/dirname"
MYSCRIPTDIR=`(cd \`${DIRNAMECMD} ${0}\` ; echo \`pwd\`)`


jarfile=$SCRIPT_START_DIR/$1

#e.g. Disruptor,BusySpinWaitStrategy,50,600;Disruptor,YieldingWaitStrategy,50,600;Disruptor,SleepingWaitStrategy,50,600;BlockingQueue,X,50,600
cases_as_string=${2:-"Disruptor,SleepingWaitStrategy,50,600"}

IFS=';' read -r -a test_cases <<< "$cases_as_string"

for test_case in "${test_cases[@]}"
do
    cd ${MYSCRIPTDIR}

    echo "input : $test_case"

    queue_type=$(echo $test_case | cut -d',' -f1)
    strategy=$(echo $test_case | cut -d',' -f2)
    background_rate_per_second=$(echo $test_case | cut -d',' -f3)
    background_rate_per_min=$((60*$background_rate_per_second))
    duration_in_second=$(echo $test_case | cut -d',' -f4)
    test_name=${queue_type}_${strategy}_bg${background_rate_per_second}perSec_$(date '+%Y%m%d_%H%M%S')_duration${duration_in_second}Sec

    echo "$test_name"

    bash run_single_scenario.sh ${test_name} ${jarfile} ${queue_type} ${strategy} ${background_rate_per_min} ${duration_in_second}

    cd ${MYSCRIPTDIR}
    zip -r zip_${test_name}.zip ${test_name}
    rm -rf ${test_name}

done

#!/bin/bash -e

which jq &>/dev/null || { echo "you need jq installed" ; exit 1 ; }


function print_start_time()
{
        cur_date1=$(jq '.start_times[1]' _docs/infos.json | sed 's/000$//')
        cur_date2=$(jq '.start_times[2]' _docs/infos.json | sed 's/000$//')

        cur_date1=$(date --date="@$cur_date1")
        cur_date2=$(date --date="@$cur_date2")

        echo "DÉPARTS DES COURSES dans infos.json:"
        echo "  Course du soir  : $cur_date1"
        echo "  Course du matin : $cur_date2"
        if [ -z $1 ]; then
          echo
          echo "Pour changer, $0 --test OU $0 <DAY>"
        fi
}

if [ "$1" = "--help" ]
then
        cat <<EOF
$0 [--help] [--test] <day> : set all the start times in infos.json
if no argument is given, display the date in the current infos.json
        --test : set the start_times of race 0 in 15 minutes, and the start_times of race 1 in 30 minutes
        <day>  : if specified, set the start_time of race 0 at the day @7p.m. and the race 1 at day+1 5a.m. WARN: day must be an English date.

EXAMPLE: $0 "9 May 2018"
EOF
        exit 0
elif [ -z "$1" ] || [ $1 == "--info" ]
then
        print_start_time $1
        exit 0
elif [ "$1" = "--test" ]
then
        date1=$(date --date="+15 minutes" +%s)
        date2=$(date --date="+30 minutes" +%s)
else
        date1=$(date --date="$@ 19:00:00 (CET DST)" +%s)
        date2=$(( $date1 + 10 * 3600)) # 10 hours later
fi

jq ". + {start_times: [0,${date1}000,${date2}000,${date1}000,0,0,0]}" _docs/infos.json > _docs/infos.json.tmp && mv _docs/infos.json.tmp _docs/infos.json
print_start_time


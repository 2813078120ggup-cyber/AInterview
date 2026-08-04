#!/usr/bin/env bash

set -uo pipefail

WORKSPACE="/workspace"
INPUT_DIR="${WORKSPACE}/input"
CASE_INPUT_DIR="${INPUT_DIR}/cases"
CLASS_DIR="${WORKSPACE}/classes"
OUTPUT_DIR="${WORKSPACE}/output"
CASE_OUTPUT_DIR="${OUTPUT_DIR}/cases"

CONFIG_FILE="${INPUT_DIR}/config.properties"
SOURCE_FILE="${INPUT_DIR}/Main.java"

mkdir -p "${CLASS_DIR}" "${CASE_OUTPUT_DIR}"

property() {
    local key="$1"
    local default_value="$2"
    local value=""

    if [[ -f "${CONFIG_FILE}" ]]; then
        value="$(
            awk -F= -v target="${key}" '
                $1 == target {
                    sub(/^[^=]*=/, "")
                    print
                    exit
                }
            ' "${CONFIG_FILE}"
        )"
    fi

    if [[ -z "${value}" ]]; then
        printf '%s' "${default_value}"
    else
        printf '%s' "${value}"
    fi
}

write_summary() {
    local status="$1"
    local passed="$2"
    local total="$3"
    local max_time="$4"
    local max_memory="$5"

    cat > "${OUTPUT_DIR}/summary.properties" <<EOF
status=${status}
passedCount=${passed}
totalCount=${total}
maxTimeMs=${max_time}
maxMemoryKb=${max_memory}
EOF
}

normalize_output() {
    local source="$1"
    local destination="$2"

    awk '
        {
            sub(/[ \t\r]+$/, "")
            lines[NR] = $0
        }
        END {
            last = NR

            while (last > 0 && lines[last] == "") {
                last--
            }

            for (i = 1; i <= last; i++) {
                print lines[i]
            }
        }
    ' "${source}" > "${destination}"
}

COMPILE_TIMEOUT="$(
    property "compileTimeoutSeconds" "15"
)"
TIME_LIMIT_MS="$(
    property "timeLimitMs" "3000"
)"
JAVA_XMX_MB="$(
    property "javaXmxMb" "128"
)"
OUTPUT_LIMIT_KB="$(
    property "outputLimitKb" "256"
)"
COMPARE_OUTPUT="$(
    property "compareOutput" "true"
)"

if [[ ! -f "${SOURCE_FILE}" ]]; then
    echo "Main.java 不存在" > "${OUTPUT_DIR}/compile.stderr"
    write_summary "SYSTEM_ERROR" 0 0 0 0
    exit 0
fi

# 只编译一次。
timeout \
    --signal=TERM \
    --kill-after=1s \
    "${COMPILE_TIMEOUT}s" \
    javac \
    -encoding UTF-8 \
    -d "${CLASS_DIR}" \
    "${SOURCE_FILE}" \
    > "${OUTPUT_DIR}/compile.stdout" \
    2> "${OUTPUT_DIR}/compile.stderr"

compile_exit_code=$?

if [[ "${compile_exit_code}" -ne 0 ]]; then
    if [[ "${compile_exit_code}" -eq 124 ]]; then
        write_summary "COMPILE_TIMEOUT" 0 0 0 0
    else
        write_summary "COMPILE_ERROR" 0 0 0 0
    fi

    exit 0
fi

mapfile -t input_files < <(
    find "${CASE_INPUT_DIR}" \
        -maxdepth 1 \
        -type f \
        -name '*.in' \
        | sort
)

total_count="${#input_files[@]}"
passed_count=0
max_time_ms=0
max_memory_kb=0
final_status="ACCEPTED"

timeout_seconds="$(
    awk -v ms="${TIME_LIMIT_MS}" \
        'BEGIN { printf "%.3fs", ms / 1000.0 }'
)"

for input_file in "${input_files[@]}"; do
    file_name="$(basename "${input_file}")"
    case_id="${file_name%.in}"

    expected_file="${CASE_INPUT_DIR}/${case_id}.expected"
    case_output="${CASE_OUTPUT_DIR}/${case_id}"

    mkdir -p "${case_output}"

    if [[ ! -f "${expected_file}" ]]; then
        echo "SYSTEM_ERROR" > "${case_output}/status"
        echo "标准输出文件不存在" > "${case_output}/stderr"

        if [[ "${final_status}" == "ACCEPTED" ]]; then
            final_status="SYSTEM_ERROR"
        fi

        continue
    fi

    start_ms="$(date +%s%3N)"

    (
        # Bash 的文件大小限制会被 Java 子进程继承。
        # 额外保留少量空间，最终仍按实际字节数判断。
        ulimit -f "$((OUTPUT_LIMIT_KB + 64))"

        timeout \
            --signal=TERM \
            --kill-after=1s \
            "${timeout_seconds}" \
            /usr/bin/time \
            -f '%M' \
            -o "${case_output}/memory_kb" \
            java \
            -Xms16m \
            -Xmx"${JAVA_XMX_MB}m" \
            -XX:+UseSerialGC \
            -cp "${CLASS_DIR}" \
            Main \
            < "${input_file}" \
            > "${case_output}/stdout" \
            2> "${case_output}/stderr"
    )

    run_exit_code=$?
    end_ms="$(date +%s%3N)"
    elapsed_ms="$((end_ms - start_ms))"

    printf '%s\n' "${elapsed_ms}" \
        > "${case_output}/time_ms"

    memory_kb=0

    if [[ -s "${case_output}/memory_kb" ]]; then
        memory_kb="$(
            tr -cd '0-9' \
                < "${case_output}/memory_kb"
        )"

        memory_kb="${memory_kb:-0}"
    fi

    stdout_size=0

    if [[ -f "${case_output}/stdout" ]]; then
        stdout_size="$(
            wc -c < "${case_output}/stdout"
        )"
    fi

    if (( elapsed_ms > max_time_ms )); then
        max_time_ms="${elapsed_ms}"
    fi

    if (( memory_kb > max_memory_kb )); then
        max_memory_kb="${memory_kb}"
    fi

    case_status=""

    if (( stdout_size > OUTPUT_LIMIT_KB * 1024 )); then
        case_status="OUTPUT_LIMIT_EXCEEDED"

    elif [[ "${run_exit_code}" -eq 124 ]] ||
         [[ "${run_exit_code}" -eq 137 ]]; then
        case_status="TIME_LIMIT_EXCEEDED"

    elif [[ "${run_exit_code}" -ne 0 ]]; then
        case_status="RUNTIME_ERROR"

    elif [[ "${COMPARE_OUTPUT}" != "true" ]]; then
        # 运行模式：不比较输出，执行成功即视为通过
        case_status="ACCEPTED"

    else
        normalize_output \
            "${case_output}/stdout" \
            "${case_output}/stdout.normalized"

        normalize_output \
            "${expected_file}" \
            "${case_output}/expected.normalized"

        if cmp -s \
            "${case_output}/stdout.normalized" \
            "${case_output}/expected.normalized"; then

            case_status="ACCEPTED"
            passed_count="$((passed_count + 1))"
        else
            case_status="WRONG_ANSWER"
        fi
    fi

    printf '%s\n' "${case_status}" \
        > "${case_output}/status"

    if [[ "${final_status}" == "ACCEPTED" ]] &&
       [[ "${case_status}" != "ACCEPTED" ]]; then
        final_status="${case_status}"
    fi
done

write_summary \
    "${final_status}" \
    "${passed_count}" \
    "${total_count}" \
    "${max_time_ms}" \
    "${max_memory_kb}"

# 判题结果通过文件返回。
# 业务上的 CE、WA、TLE 都不是容器系统异常，因此退出码保持 0。
exit 0

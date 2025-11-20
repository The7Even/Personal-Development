#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <windows.h>

// Highly optimized MapleStory Starforce simulator (single-file, multithreaded, xorshift RNG,
// precomputed tables, integer probability thresholds, per-thread accumulation)
// Compile with MSVC (cl) or Visual Studio. Designed for performance.

// ------------------------ Configuration / Constants ------------------------
#define MAX_STAR 30
#define MAX_THREADS 64

// original probability arrays (as in user's code)
static const float suc_f[30] = { 95,90,85,85,80,75,70,65,60,55,50,45,40,35,30,30,30,15,15,15,30,15,15,10,10,10,7,5,3,1 };
static const float explode_f[15] = { 1.47f,1.47f,4.76f,4.76f,5.95f,7.35f,8.93f,17.0f,18.0f,18.0f,18.0f,18.6f,19.0f,19.4f,19.8f };
static const int price_div[20] = { 571,314,214,157,200,150,70,45,200,125,200,200,200,200,200,200,200,200,200,200 };

// Scaled thresholds (0..9999)
static int suc_thr[30];
static int explode_thr[15];

// Precomputed pow table for (star+1)^2.7 up to MAX_STAR
static double pow_table[MAX_STAR + 2];

// fast RNG: xorshift32
static inline unsigned int xorshift32(unsigned int* state) {
    unsigned int x = *state;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    return *state = x ? x : 0xdeadbeefu; // avoid zero state
}

// add_commas from original but slightly tuned; returns heap-allocated string
char* add_commas(long long value) {
    char buf[64];
    sprintf_s(buf, sizeof(buf), "%lld", value);
    int len = (int)strlen(buf);
    int is_negative = (buf[0] == '-');
    int num_len = len - is_negative;
    int comma_count = (num_len - 1) / 3;
    int result_len = len + comma_count;
    char* result = (char*)malloc(result_len + 1);
    if (!result) return NULL;
    int i = len - 1;
    int j = result_len - 1;
    int digit = 0;
    while (i >= is_negative) {
        result[j--] = buf[i--];
        if (++digit == 3 && i >= is_negative) {
            result[j--] = ',';
            digit = 0;
        }
    }
    if (is_negative) result[0] = '-';
    result[result_len] = '\0';
    return result;
}

// Structure to hold per-thread results
typedef struct {
    unsigned int seed;
    int itemprice;
    int itemlvl;
    int target;
    int repeat;

    // results
    long long totalused;
    long long trials;
    long long broken;
    int* exstat; // array length max(0, target-15)
} thread_result_t;

// Thread worker function
DWORD WINAPI worker_thread(LPVOID arg) {
    thread_result_t* res = (thread_result_t*)arg;
    unsigned int rng = res->seed;
    int itemprice = res->itemprice;
    int itemlvl = res->itemlvl;
    int target = res->target;
    int localRepeat = res->repeat;

    long long local_totalused = 0;
    long long local_trials = 0;
    long long local_broken = 0;

    // local exstat array only if needed
    int exstat_local_size = 0;
    if (target > 15) exstat_local_size = target - 15;
    int* ex_local = NULL;
    if (exstat_local_size > 0) {
        ex_local = (int*)calloc(exstat_local_size, sizeof(int));
    }

    // precompute some constants
    double itemlvl3 = (double)itemlvl * itemlvl * itemlvl;

    for (int rep = 0; rep < localRepeat; ++rep) {
        int starlevel = 0;
        // Each attempt until reach target
        while (starlevel != target) {
            local_trials++;
            // cost
            if (starlevel > 9) {
                // original: 1000 + (pow(itemlvl,3) * pow(starlevel + 1, 2.7) / price[starlevel - 10]);
                // use precomputed pow_table
                double costd = 1000.0 + (itemlvl3 * pow_table[starlevel + 1] / (double)price_div[starlevel - 10]);
                local_totalused += (long long)costd;
            }
            else {
                // 1000 + (itemlvl^3 * (starlevel+1) / 36)
                double costd = 1000.0 + (itemlvl3 * (double)(starlevel + 1) / 36.0);
                local_totalused += (long long)costd;
            }

            // RNG scaled 0..9999
            unsigned int r = xorshift32(&rng) % 10000u;
            if ((int)r < suc_thr[starlevel]) {
                // success
                ++starlevel;
            }
            else {
                // failure zone: if starlevel > 14, possible destruction
                if (starlevel > 14) {
                    int idx = starlevel - 15;
                    if (idx < 15 && (int)r >= (10000 - explode_thr[idx])) {
                        // destroyed
                        local_broken++;
                        if (ex_local) ex_local[idx]++;
                        local_totalused += itemprice; // add item price
                        starlevel = 12; // reset to 12 on destruction
                    }
                    // else failure but not destroyed -> no star change
                }
                // for star <=14, nothing happens on failure other than no success
            }
        }
    }

    // write back results
    res->totalused = local_totalused;
    res->trials = local_trials;
    res->broken = local_broken;
    if (ex_local) {
        // copy ex_local into res->exstat (which points to per-thread buffer of same size)
        // NOTE: res->exstat was allocated by main with size exstat_local_size
        memcpy(res->exstat, ex_local, exstat_local_size * sizeof(int));
        free(ex_local);
    }
    res->seed = rng; // update seed
    return 0;
}

int main(void) {
    // Input
    int itemprice = 0, itemlvl = 0, target = 0, repeat_total = 0;
    printf("Input item's price. \n >>> ");
    if (scanf_s("%d", &itemprice) != 1) return 1;
    printf("Input item's level. \n >>> ");
    if (scanf_s("%d", &itemlvl) != 1) return 1;
    printf("\nInput Target Starforce Lvl. \n >>> ");
    if (scanf_s("%d", &target) != 1) return 1;
    printf("\nHow much times of simulation? \n >>> ");
    if (scanf_s("%d", &repeat_total) != 1) return 1;
    if (repeat_total <= 0) return 1;

    // Prepare thresholds and pow table
    for (int i = 0; i < 30; ++i) suc_thr[i] = (int)floorf(suc_f[i] * 100.0f + 0.0001f); // 0..10000
    for (int i = 0; i < 15; ++i) explode_thr[i] = (int)floorf(explode_f[i] * 100.0f + 0.0001f);
    for (int i = 0; i <= MAX_STAR + 1; ++i) pow_table[i] = pow((double)i, 2.7);

    // Threading setup
    SYSTEM_INFO sysinfo;
    GetSystemInfo(&sysinfo);
    int num_cpus = (int)sysinfo.dwNumberOfProcessors;
    if (num_cpus < 1) num_cpus = 1;
    int num_threads = num_cpus;
    if (num_threads > MAX_THREADS) num_threads = MAX_THREADS;

    // divide repeats across threads
    int base = repeat_total / num_threads;
    int rem = repeat_total % num_threads;

    // allocate thread results
    thread_result_t* tres = (thread_result_t*)malloc(sizeof(thread_result_t) * num_threads);
    HANDLE* threads = (HANDLE*)malloc(sizeof(HANDLE) * num_threads);

    // if exstat needed, allocate per-thread arrays and zero them
    int exstat_size = (target > 15) ? (target - 15) : 0;

    for (int t = 0; t < num_threads; ++t) {
        tres[t].seed = (unsigned int)(GetTickCount64() ^ (uintptr_t)&t ^ (unsigned int)(t * 0x9e3779b9));
        tres[t].itemprice = itemprice;
        tres[t].itemlvl = itemlvl;
        tres[t].target = target;
        tres[t].repeat = base + (t < rem ? 1 : 0);
        tres[t].totalused = 0;
        tres[t].trials = 0;
        tres[t].broken = 0;
        if (exstat_size > 0) {
            tres[t].exstat = (int*)calloc(exstat_size, sizeof(int));
        }
        else {
            tres[t].exstat = NULL;
        }
    }

    // Launch threads
    for (int t = 0; t < num_threads; ++t) {
        threads[t] = CreateThread(NULL, 0, worker_thread, &tres[t], 0, NULL);
        if (!threads[t]) {
            // fallback: run in main thread if thread creation fails
            worker_thread(&tres[t]);
            threads[t] = NULL;
        }
    }

    // Wait for threads to finish
    for (int t = 0; t < num_threads; ++t) {
        if (threads[t]) WaitForSingleObject(threads[t], INFINITE);
    }

    // Aggregate results
    long long total_used = 0;
    long long total_trials = 0;
    long long total_broken = 0;
    int* exstat_tot = NULL;
    if (exstat_size > 0) {
        exstat_tot = (int*)calloc(exstat_size, sizeof(int));
    }
    for (int t = 0; t < num_threads; ++t) {
        total_used += tres[t].totalused;
        total_trials += tres[t].trials;
        total_broken += tres[t].broken;
        if (exstat_size > 0 && tres[t].exstat) {
            for (int i = 0; i < exstat_size; ++i) exstat_tot[i] += tres[t].exstat[i];
        }
    }

    // Free per-thread exstat
    for (int t = 0; t < num_threads; ++t) if (tres[t].exstat) free(tres[t].exstat);

    // Cleanup
    free(tres);
    if (threads) free(threads);

    // Compute averages
    double avgtr = (double)total_trials / (double)repeat_total;
    double avgde = (double)total_broken / (double)repeat_total;
    double avguse = (double)total_used / (double)repeat_total;

    char* formatted = add_commas(total_used);

    // Format numbers with commas
    char* trials_str = add_commas(total_trials);
    char* broken_str = add_commas(total_broken);

    // Average formatting: prepare fallback string with two decimals, and commaized integer part
    char buf_avguse[64];
    sprintf_s(buf_avguse, sizeof(buf_avguse), "%.2f", avguse);
    // Use integer part for comma insertion; show fallback if add_commas fails
    char* avguse_str = add_commas((long long)avguse);

    char buf_avgtr[64];
    sprintf_s(buf_avgtr, sizeof(buf_avgtr), "%.2f", avgtr);
    char* avgtr_str = add_commas((long long)avgtr);

    char buf_avgde[64];
    sprintf_s(buf_avgde, sizeof(buf_avgde), "%.2f", avgde);
    char* avgde_str = add_commas((long long)avgde);

    // Final output (minimal prints)
    system("cls");
    printf("--------------------------final result--------------------------\n");
    printf("Target Star Force : %d\n", target);
    printf("Processed Simulations Amount : %d\n", repeat_total);
    printf("Total used Meso : %s\n", formatted ? formatted : "(memfail)");
    if (avguse_str) {
        // combine integer part with decimal fraction from buf_avguse
        const char* dot = strchr(buf_avguse, '.');
        if (dot) {
            printf("Average used Meso : %s%s\n", avguse_str, dot);
        }
        else {
            printf("Average used Meso : %s\n", avguse_str);
        }
    }
    else {
        printf("Average used Meso : %s\n", buf_avguse);
    }

    printf("Total Trials : %s\n", trials_str ? trials_str : "(memfail)");
    if (avgtr_str) {
        const char* dot = strchr(buf_avgtr, '.');
        if (dot) printf("Average Trials : %s%s\n", avgtr_str, dot);
        else printf("Average Trials : %s\n", avgtr_str);
    }
    else {
        printf("Average Trials : %s\n", buf_avgtr);
    }

    if (target > 15) {
        printf("Total Destructions : %s\n", broken_str ? broken_str : "(memfail)");
        if (avgde_str) {
            const char* dot = strchr(buf_avgde, '.');
            if (dot) printf("Average Destructions : %s%s\n", avgde_str, dot);
            else printf("Average Destructions : %s\n", avgde_str);
        }
        else {
            printf("Average Destructions : %s\n", buf_avgde);
        }
        printf("------------------------explode status--------------------------\n");
        for (int i = 0; i < exstat_size; ++i) {
            int star_from = i + 15;
            int star_to = i + 16;
            int times = exstat_tot ? exstat_tot[i] : 0;
            double pct = total_broken ? (double)times / (double)total_broken * 100.0 : 0.0;
            char* times_str = add_commas(times);
            printf("%d > %d : %s times (%.2f%%)\n", star_from, star_to, times_str ? times_str : "(memfail)", pct);
            if (times_str) free(times_str);
        }
    }
    printf("----------------------------------------------------------------\n");

    // free temporary strings
    if (formatted) free(formatted);
    if (trials_str) free(trials_str);
    if (broken_str) free(broken_str);
    if (avguse_str) free(avguse_str);
    if (avgtr_str) free(avgtr_str);
    if (avgde_str) free(avgde_str);
    if (exstat_tot) free(exstat_tot);
    return 0;
}

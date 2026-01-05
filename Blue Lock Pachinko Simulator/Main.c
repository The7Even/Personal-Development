#include <stdio.h>
#include <stdlib.h>
#include <Windows.h>

// 시작하기전 기본 정보 적어놓기
// 1. 기본 당첨 확률 1/399.9 > 0.25%, 첫당첨시 지급 구슬 수 1050발.
// 2. 블루록 파칭코는 전락형 LT를 보유중임. LT중 당첨률 1/33.2(3.01%), 전락율 1/108(0.92%)
// 3. 첫아타리 후 러시 돌입율은 55.7%, 이중 300발후 러시 패턴도 존재하지만 이는 생략.
// 4. LT중 당첨시 1500~7500으로 1차 당첨, 이후 7500일 경우 일정 확률로 1500, 3000, 4500, 7500중 한 수치가 골라져 추가 당첨
// 4-1. 1500~7500 당첨 확률 : 36.5%, 38%, 19.8%, 5.2%, 0.5% (7500 이후 후속 확률도 동일.)
// 5. 마지막으로 한 게임당 필요한 구슬 수는 14개로 가정. 1구슬 = 40원
// 6. 1구슬을 넣어야 15구슬을 주는 당첨 구조 덕분에 1500은 실다마 1400발임. + 1050발은 실다마 980발.
// 6-1. 대신 LT중엔 구슬 소모가 없음.

int result = 0;
int bonus7500 = 0;
int last = 0;
int trial = 0;
int gotballs = 0;
int streak = 0;
int rget = 0;
int Question = 0;
int fin7500 = 0;
int record = 0;

// 역대 기록
int hwin = 0;
int htrial = 0;
int hone = 0;
int hstreak = 0;

// 확률 테이블
int basegot = 25;

int luckygot = 301;
int luckyfail = 403; // luckygot + 92

int got1500 = 3650;
int got3000 = 7450;
int got4500 = 9430;
int got6000 = 9950;

int luckytrigger = 5570;

unsigned int seed = 0;

void init_seed() {
    seed = (unsigned int)(GetTickCount64() ^ (uintptr_t)&seed ^ GetCurrentThreadId());
    if (seed == 0)
        seed = 0xdeadbeaf;
}

// xorshift32 알고리즘 함수
unsigned int xorshift32() {
    static unsigned int x = 0; // 초기 상태 값
    if (x == 0)
    {
        init_seed();
        x = seed;
        if (x == 0)
            x = 0xdeadbeaf;
    }

    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    return x;
}

// 정수 난수 생성 + result에 바로 넣기
unsigned int randomresult() {
    unsigned int rand_int = xorshift32();
    result = 1 + (rand_int % 10000);
}

// LT중 당첨
void LTget() {
    bonus7500 = 0;
    fin7500 = 0;
    last = 0;
    streak++;
    printf("\n%d連, ", streak);
    while (9 < 10) {
        randomresult();
        if (result < got1500)
        {
            printf("1500玉 get!\n");
            gotballs = gotballs + 1400;
            rget = rget + 1500;
            last = 1500;
            break;
        }
        else if (result < got3000)
        {
            printf("3000玉 get!\n");
            gotballs = gotballs + 2800;
            rget = rget + 3000;
            last = 3000;
            break;
        }
        else if (result < got4500)
        {
            printf("4500玉 get!\n");
            gotballs = gotballs + 4200;
            rget = rget + 4500;
            last = 4500;
            break;
        }
        else if (result < got6000)
        {
            printf("6000玉 get!\n");
            gotballs = gotballs + 5600;
            rget = rget + 6000;
            last = 6000;
            break;
        }
        else
        {
            printf("7500玉 Plus ");
            gotballs = gotballs + 7000;
            rget = rget + 7500;
            bonus7500++;
            fin7500++;
        }
    }
    if (bonus7500 > 0)
        printf("total Get : %d玉\n", bonus7500 * 7500 + last);
}

void LTgetNomsg() {
    bonus7500 = 0;
    last = 0;
    streak++;
    while (9 < 10) {
        randomresult();
        if (result < got1500)
        {
            gotballs = gotballs + 1400;
            rget = rget + 1500;
            last = 1500;
            break;
        }
        else if (result < got3000)
        {
            gotballs = gotballs + 2800;
            rget = rget + 3000;
            last = 3000;
            break;
        }
        else if (result < got4500)
        {
            gotballs = gotballs + 4200;
            rget = rget + 4500;
            last = 4500;
            break;
        }
        else if (result < got6000)
        {
            gotballs = gotballs + 5600;
            rget = rget + 6000;
            last = 6000;
            break;
        }
        else
        {
            gotballs = gotballs + 7000;
            rget = rget + 7500;
            bonus7500++;
        }
    }
    if (hwin < bonus7500 * 7500 + last)
        hwin = bonus7500 * 7500 + last;
}


int main(void)
{
    int time = 0;
    printf("Select Mode.\n");
    printf("Mode 1. Single Game\n");
    printf("Mode 2. Multiple Games\n");
    printf("Mode 3. Until 7500\n");
    printf("Mode 4. Until 15000\n");
    printf("Mode 5. Target to X\n>>>");
    scanf_s("%d", &time);
    if (time == 2)
    {
        printf("\nHow much time do you want to simulate?\n>>>");
        scanf_s("%d", &time);
    }
    else if (time == 1)
    {
        time = -1;
    }
    else if (time == 3)
    {
        time = -2;
    }
    else if (time == 4)
    {
        time = -3;
    }
    else if (time == 5)
    {
        time = 10;
    }

    if (time == -1)
    {
        while (0 < 1) {
            system("cls");
            trial = 0;
            gotballs = 0;
            streak = 0;
            rget = 0;
            // 첫 당첨까지 돌리기
            do {
                randomresult();
                trial++;
            } while (result > basegot);

            // 당첨 이후 LT판정
            printf("%d spins, 1050 balls get!\n", trial);
            gotballs = gotballs + 980;
            rget = rget + 1050;

            randomresult();
            if (result < luckytrigger)
            {
                // LT 진입 성공.
                printf("Special Fever!\n");
                while (0 < 1) {
                    randomresult();
                    if (result < luckygot) // LT중 당첨
                    {
                        LTget();
                    }
                    else if (result < luckyfail) // LT 전락
                        break; // X발 LT 끝남
                }

                printf("\n-----Special Fever Finished-----\n");
                printf("%d Ren Played.\n", streak);
                printf("Total Got : %d 玉.\n", rget);
            }
            else // 시x LT 들어가지도 못함
                printf("\nfailed to Get Special Fever...\n");

            printf("\n----------Result----------\n");
            printf("%d 玉 Used. ￦%d Spent.\n", trial * 14, trial * 560);
            printf("You have %d 玉.\n", gotballs);
            printf("Your Balance : ￦ %d\n", (gotballs - (trial * 14)) * 40);

            Question = 1;

            printf("\nPlay More? (0 to stop, other to go)>>>");
            scanf_s("%d", &Question);

            if (Question == 0)
                break;
        }
    }
    else if (time == -2 || time == -3 || time == 10)
    {
        int tarpos = 0;
        if (time < 0)
            time = time * -1;
        if (time == 10)
        {
            printf("How much do you want to get?\n>>>");
            scanf_s("%d", &tarpos);
        }
        while (0 < 1) {
            record++;
            system("cls");
            trial = 0;
            gotballs = 0;
            streak = 0;
            rget = 0;
            // 첫 당첨까지 돌리기
            do {
                randomresult();
                trial++;
            } while (result > basegot);

            // 당첨 이후 LT판정
            printf("%d spins, 1050 balls get!\n", trial);
            gotballs = gotballs + 980;
            rget = rget + 1050;

            randomresult();
            if (result < luckytrigger)
            {
                // LT 진입 성공.
                printf("Special Fever!\n");
                while (0 < 1) {
                    randomresult();
                    if (result < luckygot) // LT중 당첨
                    {
                        LTget();
                    }
                    else if (result < luckyfail) // LT 전락
                        break; // X발 LT 끝남
                }

                printf("\n-----Special Fever Finished-----\n");
                printf("%d Ren Played.\n", streak);
                printf("Total Got : %d 玉.\n", rget);
            }
            else // 시x LT 들어가지도 못함
                printf("\nfailed to Get Special Fever...\n");

            if (tarpos == 0 && fin7500 == time - 1)
                break;
            else if (tarpos != 0 && tarpos <= rget)
                break;
        }

        printf("\n----------Result----------\n");
        if (tarpos == 0)
            printf("Got %d in trial %d (%.4f%%)\n", (time - 1) * 7500, record, 1.0 / record * 100);

        printf("%d 玉 Used. ￦%d Spent.\n", trial * 14, trial * 560);
        printf("You have %d 玉.\n", gotballs);
        printf("Your Balance : ￦ %d\n", (gotballs - (trial * 14)) * 40);
    }
    else {
        long long totalused = 0;
        long long totalballs = 0;
        long long totalrget = 0;
        for (int a = 0; a < time; a++)
        {
            trial = 0;
            gotballs = 0;
            streak = 0;
            rget = 0;
            // 첫 당첨까지 돌리기
            do {
                randomresult();
                trial++;
            } while (result > basegot);

            if (trial > htrial)
                htrial = trial;

            // 당첨 이후 LT판정
            gotballs = gotballs + 980;
            rget = rget + 1050;

            randomresult();
            if (result < luckytrigger)
            {
                // LT 진입 성공.
                while (0 < 1) {
                    randomresult();
                    if (result < luckygot) // LT중 당첨
                    {
                        LTgetNomsg();
                    }
                    else if (result < luckyfail) // LT 전락
                        break; // X발 LT 끝남
                }
            }

            totalused = totalused + (trial * 14);
            totalballs = totalballs + gotballs;
            totalrget = totalrget + rget;

            if (streak > hstreak)
                hstreak = streak;

            if (rget > hone)
                hone = rget;
        }

        printf("\n----------Result----------\n");
        printf("%d Fevers Played.\n", time);
        printf("%lld balls Used. ￦ %lld Spent.\n", totalused, totalused * 40);
        printf("You have %lld 玉.\n", totalballs);
        printf("Your Balance : ￦ %lld\n", (totalballs - totalused) * 40);
        printf("Highest 1 Game Win : %d 玉\n", hwin);
        printf("Highest 1 Fever Win : %d 玉\n", hone);
        printf("Highest Streak : %d\n", hstreak);
        printf("Highest Spins to get Fever : %d games\n", htrial);

    }

    printf("\nFinished Spinning. Good Bye!");
}

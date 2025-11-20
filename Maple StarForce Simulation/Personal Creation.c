#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <windows.h>
#include <string.h>

// 확률테이블, 파괴이력, 가격계산식용 테이블
float suc[30] = { 95, 90, 85, 85, 80, 75, 70, 65, 60, 55, 50, 45, 40, 35, 30, 30, 30, 15, 15, 15, 30, 15, 15, 10, 10, 10, 7, 5, 3, 1 };
float explode[15] = { 1.47, 1.47, 4.76, 4.76, 5.95, 7.35, 8.93, 17, 18, 18, 18, 18.6, 19, 19.4, 19.8 };
int exstat[15] = { 0 };
int price[20] = { 571, 314, 214, 157, 200, 150, 70, 45, 200, 125, 200, 200, 200, 200, 200, 200, 200, 200, 200, 200 };

// 1000단위로 쉼표를 삽입하는 함수 ( ChatGPT한테서 뜯어옴 )
char* add_commas(long long value) {
	char buf[32];
	// MSVC용 안전한 사용: 버퍼 크기 전달
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

	if (is_negative)
		result[0] = '-';

	result[result_len] = '\0';
	return result;
}

int main(void)
{
	int starlevel = 0; //현재 스타포스 레벨
	int trial = 0; // 총 시도횟수
	int broken = 0; // 파괴 횟수
	int random = 0; // 강화용 랜덤변수

	int itemprice = 0; // 아이템 가격 설정
	long long totalused = 0; // 총 사용 메소 변수
	int itemlvl = 0; // 아이템 레벨 (가격 계산용)
	printf("Input item's price. \n >>> ");
	scanf_s("%d", &itemprice);
	printf("Input item's level. \n >>> ");
	scanf_s("%d", &itemlvl);

	int target = 0;
	printf("\nInput Target Starforce Lvl. \n >>> ");
	scanf_s("%d", &target);

	int repeat = 0;
	printf("\nHow much times of simulation? \n >>> ");
	scanf_s("%d", &repeat);

	for (int i = 0; i < repeat; i++)
	{
		while (1)
		{
			system("cls");
			printf("Process... %.3f%%\n", (float)i / repeat * 100);
			trial++;
			
			// 강화 가격 지불
			if (starlevel > 9)
				totalused = totalused + 1000 + (pow(itemlvl, 3) * pow(starlevel + 1, 2.7) / price[starlevel - 10]);
			else
				totalused = totalused + 1000 + (pow(itemlvl, 3) * (starlevel + 1) / 36);

			// 강화 결과 출력
			random = rand() % 10000;
			if (random < suc[starlevel] * 100)
			{
				starlevel++;
			}
			else if (starlevel > 14) // 성공이 아닐 경우 진입. 15성 아래일경우는 파괴없으니 패스
			{
				if (random > 10000 - explode[starlevel - 15] * 100)
				{
					broken++;
					exstat[starlevel - 15]++;
					totalused = totalused + itemprice;
					starlevel = 12;
				}
			}

			if (starlevel == target) // 목표 스타포스 레벨 달성시 종료
				break;
		}

		starlevel = 0; // 다음 시도를 위한 초기화
	}

	float avgtr = (float)trial / repeat;
	float avgde = (float)broken / repeat;
	float avguse = (float)totalused / repeat;

	char* formatted = add_commas(totalused);

	printf("--------------------------final result--------------------------\n");
	printf("Target Star Force : %d\n", target); // 목표 스타포스
	printf("Processed Simulations Amount : %d\n", repeat); // 시뮬횟수
	printf("Total used Meso : %s\n", formatted); // 사용 메소
	printf("Average used Meso : %.2f\n", avguse);
	printf("Total Trials : %d\n", trial); // 시도 횟수
	printf("Average Trials : %.2f\n", avgtr);
	if (target > 15) // 목표 스타포스가 15회 이상이면 파괴도 출력
	{
		printf("Total Destructions : %d\n", broken); // 파괴 횟수
		printf("Average Destructions : %.2f\n", avgde);
		printf("------------------------explode status--------------------------\n");
		for (int i = 0; i < target-15; i++) // 15 > 16에서 29 > 30까지 출력
			printf("%d > %d : %d times (%.2f%%)\n", i + 15, i + 16, exstat[i], (double)exstat[i] / broken * 100);
	}
	printf("----------------------------------------------------------------");

	free(formatted);
	return target;
}

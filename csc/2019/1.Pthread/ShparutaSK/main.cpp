#define HAVE_STRUCT_TIMESPEC

#include <pthread.h>
#include <vector>
#include <iostream>
#include <time.h>

using namespace std;

//#define NO_ERROR 0 
#define OVERFLOW 1
#define MAX_INT 2147483647

thread_local int tls_error = 0;

void sleepcp(int milliseconds) // Cross-platform sleep function
{
	clock_t time_end;
	time_end = clock() + milliseconds * CLOCKS_PER_SEC / 1000;
	while (clock() < time_end);
}

typedef struct {
	int value; // the buffer
	bool was_consumed = false;
	pthread_mutex_t mutex_read = PTHREAD_MUTEX_INITIALIZER;
	pthread_mutex_t mutex_write = PTHREAD_MUTEX_INITIALIZER;
} buffer_t;

int N;
int max_time_milliseconds_sleep;
vector<buffer_t> sharedata_thread;

vector<pthread_t> consumers;
pthread_t producer;
pthread_t interruptor;

pthread_cond_t condition_consumer_launch = PTHREAD_COND_INITIALIZER;
pthread_mutex_t mutex_interrupt = PTHREAD_MUTEX_INITIALIZER;
bool consumer_launch = false;

int get_last_error() {
	// return per-thread error code
	return tls_error;
}


void set_last_error(int code) {
	// set per-thread error code
	tls_error = code;
}


void* producer_routine(void* arg) {
	// read data, loop through each value and update the value, notify consumer, wait for consumer to process 
	char c = ' ';
	while (c != '\n')
	{
		buffer_t buffer;
		cin >> buffer.value;
		sharedata_thread.push_back(buffer);
		c = getchar();
	}

	for (int i = 0; i < sharedata_thread.size(); i++)
	{
		pthread_mutex_lock(&sharedata_thread[i].mutex_write);
		sharedata_thread[i].value++;
		pthread_mutex_unlock(&sharedata_thread[i].mutex_write);
	}

	return NULL;
}


void* consumer_routine(void* arg) {
	int p = 0;

	pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
	pthread_cond_broadcast(&condition_consumer_launch);
	consumer_launch = true;

	// for every update issued by producer, read the value and add to sum
	for (int i = 0; i < sharedata_thread.size(); i++)
	{
		pthread_mutex_lock(&sharedata_thread[i].mutex_read);
		if (!sharedata_thread[i].was_consumed)
		{
			pthread_mutex_lock(&sharedata_thread[i].mutex_write);
			sharedata_thread[i].was_consumed = true;

			if (*(int*)arg + sharedata_thread[i].value >= MAX_INT)
			{
				set_last_error(OVERFLOW);
				printf("overflow\n");
				pthread_mutex_unlock(&sharedata_thread[i].mutex_write);
				pthread_mutex_unlock(&sharedata_thread[i].mutex_read);
				system("pause");
				exit(OVERFLOW);
			}

			*(int*)arg += sharedata_thread[i].value;
			sleepcp(rand() % max_time_milliseconds_sleep);
			pthread_mutex_unlock(&sharedata_thread[i].mutex_write);
		}
		pthread_mutex_unlock(&sharedata_thread[i].mutex_read);
	}
	return(&tls_error);
}


void* consumer_interruptor_routine(void* arg) {
	// wait for consumers to start
	pthread_mutex_lock(&mutex_interrupt);

	while (!consumer_launch)
		pthread_cond_wait(&condition_consumer_launch, &mutex_interrupt);

	pthread_mutex_unlock(&mutex_interrupt);

	// interrupt random consumer while producer is running 
	pthread_cancel(*(pthread_t *)arg);

	return NULL;
}


int run_threads() {
	int sum = 0;
	int error_code = 0;

	//read count of consumers, create a vector
	cin >> N;
	cin >> max_time_milliseconds_sleep;
	getchar();

	consumers = vector<pthread_t>(N);

	pthread_create(&producer, NULL, &producer_routine, NULL);
	pthread_join(producer, NULL);

	int num_thread_for_interrupt = rand() % N - 1;
	pthread_create(&interruptor, NULL, &consumer_interruptor_routine, &consumers[num_thread_for_interrupt]);

	// start N threads and wait until they're done
	for (int i = 0; i < N; i++)
		pthread_create(&consumers[i], NULL, &consumer_routine, &sum);

	for (int i = 0; i < N; i++)
		int r = pthread_join(consumers[i], NULL);

	pthread_join(interruptor, NULL);

	// return aggregated sum of values
	std::cout << sum << std::endl;

	system("pause");
	return error_code;
}


int main() {
	return run_threads();
}
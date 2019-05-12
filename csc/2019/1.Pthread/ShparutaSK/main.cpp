#define HAVE_STRUCT_TIMESPEC

#include <pthread.h>
#include <vector>
#include <iostream>
#include <time.h>
#include <string>
#include <limits.h>
#include <stdlib.h>
#include <errno.h>
#include <chrono>
#include <thread>
#include <ctime>
#include <cstdlib>

using namespace std;

#define OVERFLOW 1

struct Result {
	int sum;
	int error;
};

thread_local int tls_error = 0;

int N = 0;
int max_time_milliseconds_sleep = 0;
int sharedata_thread;
int actual_consumers_count = 0;

vector<pthread_t> consumers;
pthread_t producer;
pthread_t interruptor;

bool consumer_started = false;
pthread_cond_t consumer_started_cond = PTHREAD_COND_INITIALIZER;

bool producer_running = false;
pthread_cond_t producer_runnning_cond = PTHREAD_COND_INITIALIZER;

bool data_read = false;
pthread_cond_t data_read_cond = PTHREAD_COND_INITIALIZER;

bool consumer_procceed = false;
pthread_cond_t consumer_procceed_cond = PTHREAD_COND_INITIALIZER;

pthread_mutex_t mutex_consumers_counter = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t producer_consumer_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t consumer_proceed_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t consumer_started_mutex = PTHREAD_MUTEX_INITIALIZER;


int get_last_error() {
	// return per-thread error code
	return tls_error;
}


void set_last_error(int code) {
	// set per-thread error code
	tls_error = code;
}


void* producer_routine(void* arg) {
	producer_running = true;

	// wait for consumer to start
	pthread_mutex_lock(&consumer_started_mutex);
	while (!consumer_started)
		pthread_cond_wait(&consumer_started_cond, &consumer_started_mutex);
	pthread_mutex_unlock(&consumer_started_mutex);

	// read data, loop through each value and update the value, notify consumer, wait for consumer to process 
	while (cin >> sharedata_thread)
	{
		pthread_mutex_lock(&producer_consumer_mutex);
		data_read = true;
		pthread_cond_signal(&data_read_cond);
		pthread_mutex_unlock(&producer_consumer_mutex);

		pthread_mutex_lock(&consumer_proceed_mutex);
		while (!consumer_procceed)
			pthread_cond_wait(&consumer_procceed_cond, &consumer_proceed_mutex);				
		consumer_procceed = false;
		pthread_mutex_unlock(&consumer_proceed_mutex);

	}

	pthread_mutex_lock(&producer_consumer_mutex);
	producer_running = false;
	pthread_cond_broadcast(&data_read_cond);
	pthread_mutex_unlock(&producer_consumer_mutex);
	return NULL;
}


void* consumer_routine(void* arg) {
	// notify about start
	pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

	pthread_mutex_lock(&consumer_started_mutex);
	consumer_started = true;
	pthread_cond_signal(&consumer_started_cond);
	pthread_mutex_unlock(&consumer_started_mutex);

	pthread_mutex_lock(&mutex_consumers_counter);
	actual_consumers_count++;
	pthread_mutex_unlock(&mutex_consumers_counter);

	// for every update issued by producer, read the value and add to sum	
	int local_sum = 0;
	while (true)
	{
		pthread_mutex_lock(&producer_consumer_mutex);
		while (!data_read && producer_running) {
			pthread_cond_wait(&data_read_cond, &producer_consumer_mutex);
		}

		if (data_read)
		{
			int local_data = *(int*)arg;

			data_read = false;
			pthread_mutex_unlock(&producer_consumer_mutex);

			pthread_mutex_lock(&consumer_proceed_mutex);
			consumer_procceed = true;
			pthread_cond_signal(&consumer_procceed_cond);
			pthread_mutex_unlock(&consumer_proceed_mutex);

			if ((local_sum > 0 && local_data > INT_MAX - local_sum) || (local_sum < 0 && local_data < INT_MIN - local_sum)) {
				set_last_error(OVERFLOW);
				break;
			}

			local_sum += local_data;

			std::this_thread::sleep_for(std::chrono::microseconds(rand() % max_time_milliseconds_sleep*1000));
		}

		if (!producer_running)
		{
			pthread_mutex_unlock(&producer_consumer_mutex);
			break;
		}
	}

	pthread_mutex_lock(&mutex_consumers_counter);
	actual_consumers_count--;
	pthread_mutex_unlock(&mutex_consumers_counter);

	// return pointer to result (for particular consumer)
	Result *res = new Result;
	res->sum = local_sum;
	res->error = get_last_error();

	return (void *)res;
}


void* consumer_interruptor_routine(void* arg) {
	// wait for consumers to start
	pthread_mutex_lock(&consumer_started_mutex);
	while (!consumer_started)
		pthread_cond_wait(&consumer_started_cond, &consumer_started_mutex);
	pthread_mutex_unlock(&consumer_started_mutex);

	// interrupt random consumer while producer is running 
	while (actual_consumers_count > 0)
	{
		pthread_mutex_lock(&mutex_consumers_counter);
		if (actual_consumers_count > 0)
			pthread_cancel(consumers[(1 + rand() % actual_consumers_count) - 1]);
		pthread_mutex_unlock(&mutex_consumers_counter);
	}

	return NULL;
}


int run_threads() {
	int sum = 0;
	actual_consumers_count = 0;

	consumers = vector<pthread_t>(N);

	pthread_create(&producer, NULL, &producer_routine, NULL);
	pthread_create(&interruptor, NULL, &consumer_interruptor_routine, NULL);

	// start N threads and wait until they're done
	for (int i = 0; i < N; i++)
		pthread_create(&consumers[i], NULL, &consumer_routine, &sharedata_thread);

	pthread_join(producer, NULL);
	pthread_join(interruptor, NULL);

	for (int i = 0; i < N; i++) {
		Result* res;
		pthread_join(consumers[i], (void **)&res);

		if (res->error == OVERFLOW || (res->sum > 0 && sum > INT_MAX - res->sum) || (res->sum < 0 && sum < INT_MIN - res->sum))
			set_last_error(OVERFLOW);

		if (get_last_error() != OVERFLOW)
			sum += res->sum;
		delete(res);
	}


	if (get_last_error() == OVERFLOW) {
		std::cout << "overflow" << std::endl;
		return 1;
	}

	// return aggregated sum of values
	std::cout << sum << std::endl;
	//system("pause");
	return 0;
}


int main(int argc, char *argv[]) {
	N = atoi(argv[1]);
	max_time_milliseconds_sleep = atoi(argv[2]);
	return run_threads();
}
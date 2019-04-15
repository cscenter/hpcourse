#include <pthread.h>
#include <unistd.h>
#include <iostream>
#include <string>
#include <sstream>
#include <random>
#include <limits.h>



#define NOERROR 0
#define OVERFLOW 1

int n_consumers;
int n_real_consumers = 0;
unsigned int max_wait_time;
int shared_value;
bool value_updated = false;
bool finished_producing = false;

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t producer_updated_value = PTHREAD_COND_INITIALIZER;
pthread_cond_t consumer_received_value = PTHREAD_COND_INITIALIZER;
pthread_mutex_t consumers_start_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t consumers_started = PTHREAD_COND_INITIALIZER;

thread_local int error_code = NOERROR;

int get_last_error() {
	return error_code;
}

void set_last_error(int code) {
	error_code = code;
}

void wait_consumers_start() {	
	while (n_real_consumers < n_consumers) {
		pthread_cond_wait(&consumers_started, &consumers_start_mutex);
	}
}

void wait_consumers_receive_value() {
	while (value_updated) {
		pthread_cond_wait(&consumer_received_value, &mutex);
	}		
}

void wait_producer_update_value() {
	while(!value_updated) {
		pthread_cond_wait(&producer_updated_value, &mutex);
	}		
}

void* producer_routine(void* arg) {
	int* shared_value = static_cast<int*>(arg);
	std::string line;
	std::getline(std::cin, line);
	std::istringstream input(line);
	int value;
	pthread_mutex_lock(&consumers_start_mutex);
	wait_consumers_start();
	pthread_mutex_unlock(&consumers_start_mutex);	
	while (input >> value) {
		pthread_mutex_lock(&mutex);
		wait_consumers_receive_value();
		*shared_value = value;
		value_updated = true;
		pthread_cond_signal(&producer_updated_value);
		pthread_mutex_unlock(&mutex);
	}
	pthread_mutex_lock(&mutex);
	wait_consumers_receive_value();
	finished_producing = true;                                            
	value_updated = true;                                                 
	pthread_cond_broadcast(&producer_updated_value);		
	pthread_mutex_unlock(&mutex);
	return 0;
}

struct Output {	
	int value;
	int error_code;
};

Output* consumer_output(int sum) {
	Output* output = new Output;
	output->value = sum;
	output->error_code = get_last_error(); 
	return output;
}

bool check_overflow(int a, int b, int* res) {
	if ((a > 0 && b > INT_MAX - a) || (a < 0 && b < INT_MIN - a)) {
		return true;
	}
	*res = a + b;
	return false;
}
 
void* consumer_routine(void* arg) {
	pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
	pthread_mutex_lock(&consumers_start_mutex);
	++n_real_consumers;
	pthread_cond_signal(&consumers_started);
	pthread_mutex_unlock(&consumers_start_mutex);
	int* shared_value = static_cast<int*>(arg);
	int sum = 0;
	int tmp_sum;
	bool error_overflow = false;
	while (true) {
		pthread_mutex_lock(&mutex);
		wait_producer_update_value();
		if (finished_producing) {
			pthread_mutex_unlock(&mutex);
			pthread_exit(consumer_output(sum));
		}
		if (check_overflow(sum, *shared_value, &tmp_sum)) {
			set_last_error(OVERFLOW);
			error_overflow = true;
		}
		value_updated = false;
		pthread_cond_signal(&consumer_received_value);
		pthread_mutex_unlock(&mutex);
		if (error_overflow) {
			pthread_exit(consumer_output(sum));	
		}
		sum = tmp_sum;
		usleep(rand() % (max_wait_time + 1) * 1000);	
	}
}

void* consumer_interruptor_routine(void* arg) {
	pthread_t* consumers = static_cast<pthread_t*>(arg);
	pthread_mutex_lock(&consumers_start_mutex);
	wait_consumers_start();
	pthread_mutex_unlock(&consumers_start_mutex);	
	while (!finished_producing) {
		pthread_cancel(consumers[rand() % n_consumers]);	
	}
	return 0;
}
 
int run_threads() {
	int sum = 0;
	pthread_t producer;
	pthread_t consumers[n_consumers];
	pthread_t interruptor;
    pthread_create(&producer, NULL, producer_routine, &shared_value);
	for (int i = 0; i < n_consumers; ++i) {
		pthread_create(&consumers[i], NULL, consumer_routine, &shared_value);
	}
	pthread_create(&interruptor, NULL, consumer_interruptor_routine, consumers);
	pthread_join(producer, NULL);
	bool error_overflow = false;
	for (int i = 0; i < n_consumers; ++i) {
		Output* output;
		pthread_join(consumers[i], (void**) &output);
		if (output->error_code == OVERFLOW || check_overflow(sum, output->value, &sum)) {
			error_overflow = true;
		}
		delete output;
		if (error_overflow) {
			std::cout << "overflow" << std::endl;
			return 1;
		}
	}
	pthread_join(interruptor, NULL);
	std::cout << sum << std::endl;
	return 0;
}
 
int main(int argc, char** argv) {
	n_consumers = atoi(argv[1]);
	max_wait_time = atoi(argv[2]);
	return run_threads();
}

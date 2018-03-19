#define HAVE_STRUCT_TIMESPEC
#include <pthread.h>  
#include <iostream>
#include <vector>


struct Role
{
	bool launch = false;
	bool ready = false;
	bool stop = false;
};

Role consumer;
Role producer;

int value = 0;

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

pthread_cond_t condition_consumer_launch = PTHREAD_COND_INITIALIZER;
pthread_cond_t condition_consumer_ready = PTHREAD_COND_INITIALIZER;
pthread_cond_t condition_producer_ready = PTHREAD_COND_INITIALIZER;

std::vector<int> read_input()
{
	std::vector<int> data;

	int n;
	std::cin >> n;

	int element;
	while (std::cin >> element)
	{
		data.push_back(element);

		if (data.size() == n)
			break;
	}
	return data;
}

void* producer_routine(void* arg) {

	// Wait for consumer to start
	pthread_mutex_lock(&mutex);

	while (!consumer.launch)
		pthread_cond_wait(&condition_consumer_launch, &mutex);
	
	// Read data, loop through each value and update the value, notify consumer, wait for consumer to process
	std::vector<int> data = read_input();
	for (int i = 0; i < data.size(); ++i) 
	{
		value = data[i];
		producer.ready = true;

		pthread_cond_signal(&condition_producer_ready);

		while (!consumer.ready)
			pthread_cond_wait(&condition_consumer_ready, &mutex);

		consumer.ready = false;
		pthread_mutex_unlock(&mutex);
	}

	pthread_mutex_lock(&mutex);
	producer.stop = producer.ready = true;

	pthread_cond_signal(&condition_producer_ready);

	pthread_mutex_unlock(&mutex);

	pthread_exit(EXIT_SUCCESS);
	return arg;
}

void* consumer_routine(void* arg) 
{
	pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
	pthread_mutex_lock(&mutex);

	// notify about start
	pthread_cond_broadcast(&condition_consumer_launch);
	consumer.launch = true;

	pthread_mutex_unlock(&mutex);

	// allocate value for result
	int *result = new int(0);

	// for every update issued by producer, read the value and add to sum
	bool eof = false;
	while (!eof)
	{
		pthread_mutex_lock(&mutex);

		while (!producer.ready)
			pthread_cond_wait(&condition_producer_ready, &mutex);

		if (!producer.stop)
		{
			*result += value;

			producer.ready = false;
			consumer.ready = true;
			pthread_cond_signal(&condition_consumer_ready);
			pthread_mutex_unlock(&mutex);
			continue;
		}
		
		eof = true;
		producer.ready = false;
		consumer.stop = true;
		pthread_mutex_unlock(&mutex);

		// return pointer to result
		pthread_exit((void *)result);
	}

	// return pointer to result
	return (void *)result;
}

void* consumer_interruptor_routine(void* arg) 
{
	// wait for consumer to start
	pthread_mutex_lock(&mutex);

	while (!consumer.launch)
		pthread_cond_wait(&condition_consumer_launch, &mutex);

	pthread_mutex_unlock(&mutex);

	// interrupt consumer while producer is running
	while (!producer.stop)
		pthread_cancel(*(pthread_t *)arg);

	pthread_exit(NULL);
	return arg;
}

int run_threads() 
{
	int *sum;

	pthread_t producer, consumer, interruptor;

	// start 3 threads and wait until they're done
	pthread_create(&producer, NULL, producer_routine, NULL);
	pthread_create(&consumer, NULL, consumer_routine, NULL);
	pthread_create(&interruptor, NULL, consumer_interruptor_routine, (void *)&consumer);
	pthread_join(producer, NULL);
	pthread_join(consumer, (void **)&sum);
	pthread_join(interruptor, NULL);
	
	// return sum of update values seen by consumer
	int answer = *sum;
	delete sum;

	return answer;
}

int main() 
{
	std::cout << run_threads() << std::endl;
	return 0;
}

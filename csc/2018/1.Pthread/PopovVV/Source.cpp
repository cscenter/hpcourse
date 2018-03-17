#define HAVE_STRUCT_TIMESPEC

#include <pthread.h>
#include <iostream>

pthread_mutex_t data_mutex = PTHREAD_MUTEX_INITIALIZER,
				awake_mutex = PTHREAD_MUTEX_INITIALIZER;

pthread_cond_t producer_recieved = PTHREAD_COND_INITIALIZER,
			   producer_awake = PTHREAD_COND_INITIALIZER,
			   consumer_updated = PTHREAD_COND_INITIALIZER,
			   consumer_awake = PTHREAD_COND_INITIALIZER;

pthread_t consumer, 
		  producer, 
		  interruptor;

int data = 0;

bool producer_end = false,
	 data_update = false,
	 producer_start = false,
	 consumer_start = false;

void *producer_routine(void *arg) 
{
	pthread_mutex_lock(&awake_mutex);

	producer_start = true;

	pthread_cond_signal(&producer_awake);
	pthread_mutex_unlock(&awake_mutex);

	int input_number = 0;

	while (std::cin >> input_number) 
	{
		pthread_mutex_lock(&data_mutex);

		while (data_update) 
		{
			pthread_cond_wait(&consumer_updated, &data_mutex);
		}	

		data_update = true;
		data = input_number;

		pthread_cond_signal(&producer_recieved);
		pthread_mutex_unlock(&data_mutex);
	}

	pthread_mutex_lock(&data_mutex);

	while (data_update) 
	{
		pthread_cond_wait(&consumer_updated, &data_mutex);
	}

	data_update = true;
	producer_end = true;

	pthread_cond_signal(&producer_recieved);
	pthread_mutex_unlock(&data_mutex);

	return (void* )NULL;
}

void *consumer_routine(void *arg) 
{
	pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, 0);
	pthread_mutex_lock(&awake_mutex);

	while (!producer_start)
	{
		pthread_cond_wait(&producer_awake, &awake_mutex);
	}

	consumer_start = true;

	pthread_cond_signal(&consumer_awake);
	pthread_mutex_unlock(&awake_mutex);

	int sum = 0;
	bool end = false;

	while (!end) 
	{
		pthread_mutex_lock(&data_mutex);

		while (!data_update) 
		{
			pthread_cond_wait(&producer_recieved, &data_mutex);
		}

		end = producer_end;

		if (end) 
		{
			pthread_mutex_unlock(&data_mutex);
			break;
		}

		sum += data;
		data_update = false;

		pthread_cond_signal(&consumer_updated);
		pthread_mutex_unlock(&data_mutex);
	}

	return new int(sum);
}

void *interruptor_routine(void *arg) {

	pthread_mutex_lock(&awake_mutex);

	while (!consumer_start)
	{
		pthread_cond_wait(&consumer_awake, &awake_mutex);
	}

	pthread_mutex_unlock(&awake_mutex);

	bool end = false;
	pthread_t* interrupted_thread = (pthread_t*)arg;

	while (!end)
	{
		pthread_cancel(*interrupted_thread);

		pthread_mutex_lock(&data_mutex);
		end = producer_end;
		pthread_mutex_unlock(&data_mutex);
	}

	return (void* )NULL;
}

int run_threads() 
{
	int *result = 0;
	
	pthread_create(&producer, NULL, producer_routine, NULL);
	pthread_create(&consumer, NULL, consumer_routine, NULL);
	pthread_create(&interruptor, NULL, interruptor_routine, &consumer);
	
	pthread_join(producer, NULL);
	pthread_join(consumer, (void **)&result);
	pthread_join(interruptor, NULL);
	
	int sum = *result;
	delete result;
	
	return sum;
}

int main()
{

	std::cout << run_threads() << std::endl;

	return 0;

}

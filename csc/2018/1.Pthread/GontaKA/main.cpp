#include <pthread.h>
#include <iostream>
#include <vector>

int data = 0;
bool last_element = false;
bool unread_element = false;
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;;
pthread_cond_t condition = PTHREAD_COND_INITIALIZER;
pthread_cond_t consumer_launched_cond = PTHREAD_COND_INITIALIZER;
bool consumer_launched = false;

void* producer_routine(void* args) {
	std::vector<int> arr;
    int tmp;
    while(std::cin >> tmp) {
       arr.push_back(tmp);
    }
    pthread_mutex_lock(&mutex);
    for (int i = 0; i < arr.size(); i++) {
		while (unread_element) {
			pthread_cond_wait(&condition, &mutex);
		}
		data = arr[i];
		unread_element = true;
		last_element = arr.size() == i + 1;
		pthread_cond_signal(&condition);
	}
    pthread_mutex_unlock(&mutex);
    pthread_exit(NULL);
}

void* consumer_routine(void* args) {
	pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
	pthread_mutex_lock(&mutex);
    consumer_launched = true;
    pthread_cond_signal(&consumer_launched_cond);
    pthread_mutex_unlock(&mutex);


	int* result = new int(0);
	
	bool last = false;
    pthread_mutex_lock(&mutex);
	while (!last) {
		while (!unread_element) {
				pthread_cond_wait(&condition, &mutex);
		}
		int current = data;
		last = last_element;
		unread_element = false;
		pthread_cond_signal(&condition);
		*result += current;
	}
    pthread_mutex_unlock(&mutex);
	pthread_exit(result);
}

void* consumer_interruptor(void* args) {
	pthread_mutex_lock(&mutex);
    while(!consumer_launched) {
        pthread_cond_wait(&consumer_launched_cond, &mutex);
    }
    pthread_mutex_unlock(&mutex);

    bool last = false;
    while(!last) {
    	pthread_mutex_lock(&mutex);
    	last = last_element;
    	pthread_cancel(*static_cast<pthread_t *>(args));
    	pthread_mutex_unlock(&mutex);
    }
    pthread_exit(NULL);
}

int run_threads() {
    void *result;

    pthread_t producer, consumer, interruptor;
    pthread_create(&producer, NULL, producer_routine, NULL);
    pthread_create(&consumer, NULL, consumer_routine, NULL);
    pthread_create(&interruptor, NULL, consumer_interruptor, (void*) &consumer);
 	
    pthread_join(producer, NULL);
    pthread_join(consumer, &result);
    pthread_join(interruptor, NULL);
    int *casted_result = static_cast<int *>(result);
    int final_result = *casted_result;
    delete casted_result;

    return final_result;
}


int main() {
	std::cout << run_threads() << std::endl;
    return 0;
}
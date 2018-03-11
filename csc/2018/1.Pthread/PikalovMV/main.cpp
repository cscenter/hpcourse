#include <pthread.h>
#include <iostream>

pthread_barrier_t barrier;
pthread_mutex_t mutex;
pthread_cond_t produce;
pthread_cond_t consume;

int value = 0;

bool producer_finished = false;
bool update_value = false;

void *producer_routine(void *arg){

    pthread_barrier_wait(&barrier);

    int data = 0;

    while(std::cin >> data){
        pthread_mutex_lock(&mutex);

        while(update_value){
            pthread_cond_wait(&produce, &mutex);
	}

        value = data;
	update_value = true;

	pthread_cond_signal(&consume);
	pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&mutex);

    while(update_value){
        pthread_cond_wait(&produce, &mutex);
    }

    update_value = true;
    producer_finished = true;

    pthread_cond_signal(&consume);
    pthread_mutex_unlock(&mutex);
    pthread_exit(NULL);
}

void *consumer_routine(void *arg){

    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, 0);

    pthread_barrier_wait(&barrier);

    int *sum = new int(0);

    bool stop = false;

    while(!stop){
        pthread_mutex_lock(&mutex);

	while(!update_value){
            pthread_cond_wait(&consume, &mutex);
	}

        stop = producer_finished;

	if(stop == true){
            pthread_mutex_unlock(&mutex);
	    break;
	}

	*sum += value;
	update_value = false;

	pthread_cond_signal(&produce);
	pthread_mutex_unlock(&mutex);
    }
    pthread_exit((void *)sum);
}

void *consumer_interruptor_routine(void *arg){

    pthread_barrier_wait(&barrier);

    bool stop = false;

    while(!stop){
        pthread_mutex_lock(&mutex);

        stop = producer_finished;

        pthread_mutex_unlock(&mutex);
        pthread_cancel(*(pthread_t *)(arg));
    }

    pthread_exit(NULL);
}

int run_threads(){

    int *result = 0;

    pthread_barrier_init(&barrier, NULL, 3);
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&produce, NULL);
    pthread_cond_init(&consume, NULL);

    pthread_t consumer, producer, interruptor;
    
    pthread_create(&producer, NULL, producer_routine, NULL);
    pthread_create(&consumer, NULL, consumer_routine, NULL);
    pthread_create(&interruptor, NULL, consumer_interruptor_routine, (void *)consumer);

    pthread_join(producer, NULL);
    pthread_join(consumer, (void **)&result);
    pthread_join(interruptor, NULL);

    pthread_mutex_destroy(&mutex);
    pthread_barrier_destroy(&barrier);
    pthread_cond_destroy(&produce);
    pthread_cond_destroy(&consume);

    int ans = *result;
    delete result;

    return ans;
}

int main(){

    std::cout << run_threads() << std::endl;

    return 0;
}

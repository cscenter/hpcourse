//
// Pthreads task
// Sergei G. Shulman 4.03.2017
//

#include <pthread.h>  
#include <iostream>

class Value {
public:
    Value() : _value(0) {}

    void update(int value) {
        _value = value;
    }

    int get() const {
        return _value;
    }
private:
    int _value;
};

pthread_mutex_t mutex;
pthread_cond_t cond_consumer, cond_producer;
int status = 0; // 0 consumer is not ready
				// 1 consumer's time to work
				// 2 producer's time to work
				// 3 producer is ready
				// only mutex owner can check it
				
void* producer_routine(void* arg) 
{
    // Read data
	int n;
	while (std::cin >> n) 
	{
		pthread_mutex_lock(&mutex);
		// wait time to work
		while (status != 2)		       
			pthread_cond_wait(&cond_producer, &mutex);
		// update the value
		((Value*)arg)->update(n);
 		// notify consumer
 		status = 1;
		pthread_cond_signal(&cond_consumer);
			
		pthread_mutex_unlock(&mutex);	 
	}
	// notify about the end
	pthread_mutex_lock(&mutex);
	// wait time to work
	while (status != 2)		       
		pthread_cond_wait(&cond_producer, &mutex);
	// notify consumer
 	status = 3;
	pthread_cond_signal(&cond_consumer);
	
	pthread_mutex_unlock(&mutex);	
	pthread_exit(0);

}

void* consumer_routine(void* arg) {
pthread_setcancelstate (PTHREAD_CANCEL_DISABLE,  NULL);
  // allocate value for result
    int *res = new int();
	// protect the value (it is not neccesary here but for similarity)
  	pthread_mutex_lock(&mutex);
  	status = 2;
  	pthread_cond_signal(&cond_producer);
  	pthread_mutex_unlock(&mutex);
  	
  	// we will notify producer to start after adding first value,
  	// which is zero
	while (true) 
	{
    	// protect the value
		pthread_mutex_lock(&mutex);
		// wait time to work
		while (status == 2)		       
			pthread_cond_wait(&cond_consumer, &mutex);
		// all is done
		if (status == 3) break;
		// update result
		*res+=((Value*)arg)->get();
		// notify producer
		status = 2;
		pthread_cond_signal(&cond_producer);
		// free the value
		pthread_mutex_unlock(&mutex);
	}
	pthread_setcancelstate (PTHREAD_CANCEL_ENABLE,  NULL);
	pthread_exit((void**)res);
}

void* consumer_interruptor_routine(void* arg) 
{
	// when consumer will be ready it will notify the producer
	// we will see it
	// protect the value to check status
	pthread_mutex_lock(&mutex);
	while (status == 0)		       
		pthread_cond_wait(&cond_producer, &mutex);
	pthread_mutex_unlock(&mutex);
	
	// interrupt consumer many times untill it is interrupted  
	while (!pthread_cancel(*((pthread_t*)arg)))
		;   
	pthread_exit(0);
}

int run_threads() {
  // start 3 threads and wait until they're done
  // return sum of update values seen by consumer
  	pthread_t th_produser, th_consumer, th_interruptor;
  	Value v;
  	
  	// pthread stuff init
  	pthread_mutex_init(&mutex, NULL);	
  	pthread_cond_init(&cond_consumer, NULL);		
  	pthread_cond_init(&cond_producer, NULL);
    // create threads
	if (pthread_create( &th_produser, NULL, producer_routine, (void*)&v ))
		return 0;
	if (pthread_create( &th_consumer, NULL, consumer_routine, (void*)&v ))
		return 0;
	if (pthread_create( &th_interruptor, NULL, consumer_interruptor_routine, (void*)&th_consumer ))
		return 0;
	// get result	
	int *p_res;
	pthread_join(th_consumer, (void**)&p_res);
	pthread_join(th_produser, NULL);
	pthread_join(th_interruptor, NULL);
	int res = *p_res;
	delete p_res;
	// pthread stuff cancel
	pthread_mutex_destroy(&mutex);	
 	pthread_cond_destroy(&cond_consumer);		
  	pthread_cond_destroy(&cond_producer);		
	return res;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}



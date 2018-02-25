
#include <cstdlib>
#include <pthread.h>
#include <vector>
#include <iostream>
using namespace std;

/*
 * 
 */
volatile int shared_var=0;
volatile bool owner=0; 
// owner =0 - shared_var должен обрабатывать producer
// owner =1 - shared_var должен обрабатывать consumer
volatile bool exit_state=0;
pthread_mutex_t mutex1= PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t mutex2= PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond1=PTHREAD_COND_INITIALIZER;
pthread_cond_t cond2=PTHREAD_COND_INITIALIZER;

void* producer_routine(void* input){
    int n=*((int*)input);
    int* data=(int*)input;
    for(int i=1;i<=n;++i){
        pthread_mutex_lock(&mutex1);
        shared_var=data[i];
        owner=1;
        exit_state=(i==n);
        pthread_cond_signal(&cond1);
        pthread_mutex_unlock(&mutex1);
        
        
        pthread_mutex_lock(&mutex2);
        while(owner){
            pthread_cond_wait(&cond2,&mutex2);
        }
        pthread_mutex_unlock(&mutex2);
    }
}

void* consumer_routine(void* none){
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE,0);
    int result=0;
    bool exit=0;
    while(!exit){
        pthread_mutex_lock(&mutex1);
        pthread_cond_wait(&cond1,&mutex1);
        result+=shared_var;
        exit=exit_state;
        pthread_mutex_unlock(&mutex1);
        
        pthread_mutex_lock(&mutex2);
        owner=0;
        pthread_cond_broadcast(&cond2);
        pthread_mutex_unlock(&mutex2);
    }
    return new int(result);
}

void* interuptor_routine(void* victim){
    pthread_t* victim_id=(pthread_t*) victim;
    bool exit=0;
    while(!exit){
        for(int i=0;i<1000;++i)
            pthread_cancel(*victim_id);
        
        pthread_mutex_lock(&mutex1);
        exit=exit_state;
        pthread_mutex_unlock(&mutex1);
    }
}

//data[0]- length of input
//data[1...data[0]] - input
int run_threads(int* data){
    int* res=0;
    
    pthread_mutex_init(&mutex1,NULL);
    pthread_mutex_init(&mutex2,NULL);
    pthread_cond_init(&cond1,NULL);
    pthread_cond_init(&cond2,NULL);
    
    pthread_t producer,consumer,interruptor;
    
    pthread_create(&consumer,NULL,consumer_routine,NULL);
    pthread_create(&interruptor,NULL,interuptor_routine,&consumer);
    pthread_create(&producer,NULL,producer_routine,(void*)(data));
    
    pthread_join(consumer,(void**)&res);
    pthread_join(producer,NULL);
    pthread_join(interruptor,NULL);
    
    int result=*res;
    delete res;
    return result;
}
int main(int argc, char** argv) {
    int n=1000;
    int* data=new int[n+1];
    data[0]=n;
    for(int i=1;i<n;++i)
        data[i]=i;
    std::cout<<run_threads(data)<<"\n";
    delete[] data;
    return 0;
}


/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/* 
 * File:   main.cpp
 * Author: nick
 *
 * Created on 25 февраля 2018 г., 16:25
 */

#include <cstdlib>
#include <pthread.h>
#include <vector>
#include <iostream>
#include <algorithm>
#include <iterator>
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

void* producer_routine(void* arg){
    std::vector<int> data;
    //получение данных, символ конца потока - ctrl+d
    std::copy(std::istream_iterator<int>(std::cin),std::istream_iterator<int>(),std::back_inserter(data));
    for(int i=0;i<data.size();++i){
        //работа с данными
        pthread_mutex_lock(&mutex1);
        shared_var=data[i];
        owner=1;
        exit_state=(i==int(data.size())-1);
        pthread_cond_signal(&cond1);
        pthread_mutex_unlock(&mutex1);
        
        //ожидание результата работы consumer
        pthread_mutex_lock(&mutex2);
        while(owner){
            pthread_cond_wait(&cond2,&mutex2);
        }
        pthread_mutex_unlock(&mutex2);
    }
}

void* consumer_routine(void* arg){
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE,0);
    int result=0;
    bool exit=0;
    while(!exit){
        pthread_mutex_lock(&mutex1);
        //ожидание результатов producer потока
        pthread_cond_wait(&cond1,&mutex1);
        //работа с данными
        result+=shared_var;
        exit=exit_state;
        pthread_mutex_unlock(&mutex1);
        
        //информирование об окончании работы с данными
        pthread_mutex_lock(&mutex2);
        owner=0;
        pthread_cond_broadcast(&cond2);
        pthread_mutex_unlock(&mutex2);
    }
    return new int(result);
}

void* consumer_interuptor_routine(void* arg){
    pthread_t* victim_id=(pthread_t*) arg;
    bool exit=0;
    while(!exit){
        // попытки остановки потока
        for(int i=0;i<1000;++i)
            pthread_cancel(*victim_id);
        
        //обновление флага выхода
        pthread_mutex_lock(&mutex1);
        exit=exit_state;
        pthread_mutex_unlock(&mutex1);
    }
}

int run_threads(){
    int* res=0;
    
    //инциализация примитивов
    pthread_mutex_init(&mutex1,NULL);
    pthread_mutex_init(&mutex2,NULL);
    pthread_cond_init(&cond1,NULL);
    pthread_cond_init(&cond2,NULL);
    
    pthread_t producer,consumer,interruptor;
    
    //создание потоков
    pthread_create(&consumer,NULL,consumer_routine,NULL);
    pthread_create(&interruptor,NULL,consumer_interuptor_routine,&consumer);
    pthread_create(&producer,NULL,producer_routine,NULL);
    
    pthread_join(consumer,(void**)&res);
    pthread_join(producer,NULL);
    pthread_join(interruptor,NULL);
    
    pthread_mutex_destroy(&mutex1);
    pthread_mutex_destroy(&mutex2);
    pthread_cond_destroy(&cond1);
    pthread_cond_destroy(&cond2);
    
  
    int result=*res;
    delete res;
    return result;
}
int main(int argc, char** argv) {
    std::cout<<run_threads()<<"\n";
    return 0;
}


#include <pthread.h>  
#include <iostream>
#include <unistd.h>
#include <fstream>
#include <string>
#include <sstream>
#include <vector>
#include <iterator>

pthread_cond_t data_cond = PTHREAD_COND_INITIALIZER;
pthread_mutex_t data_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t consumer_mutex = PTHREAD_MUTEX_INITIALIZER;
int data;
bool end = false;
bool change = false;

void* producer_routine(void* arg) {
    std::vector<int>*v = static_cast<std::vector<int>*>(arg);
    pthread_mutex_lock(&data_mutex);
    for (auto const& number: *v){
      data = number;
      change = true;
      pthread_cond_signal(&data_cond);
      while(change){
        pthread_cond_wait(&data_cond, &data_mutex);
      }
    }
    end = true;
    change = true;
    pthread_cond_signal(&data_cond);
    pthread_mutex_unlock(&data_mutex);
}

void* consumer_routine(void* arg) {
  pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
  int& sum = *(int*) arg;
  pthread_mutex_lock(&data_mutex);
  while(!end){
    while (!change){
        pthread_cond_wait(&data_cond, &data_mutex);
    }
    if (!end){
        change = false;
        sum += data;
    }
    pthread_cond_signal(&data_cond);
  }
  pthread_mutex_unlock(&data_mutex);
}

void* consumer_interruptor_routine(void* arg) {
    pthread_t consumer = *(pthread_t*) arg;
    pthread_mutex_lock(&consumer_mutex);
    while(!end){
      pthread_cancel(consumer);
    }
    pthread_mutex_unlock(&consumer_mutex);
}

std::vector<int> read_vector(){
    std::string line;
    std::getline(std::cin, line);
    std::istringstream iss(line);
    std::vector<int> vars{std::istream_iterator<int>(iss), std::istream_iterator<int>()};
    return vars;
}

int run_threads() {
    pthread_t consumer;
    pthread_t producer;
    pthread_t interruptor;
    std::vector<int> v = read_vector();
    int sum = 0;
    pthread_create(&consumer, NULL, consumer_routine, &sum);
    pthread_create(&producer, NULL, producer_routine, &v);
    pthread_create(&interruptor, NULL, consumer_interruptor_routine, &consumer);
    pthread_join(consumer, NULL);
    pthread_join(producer, NULL);
    pthread_join(interruptor, NULL);
    return sum;
}

int main() {
    std::cout << run_threads();
    return 0;
}
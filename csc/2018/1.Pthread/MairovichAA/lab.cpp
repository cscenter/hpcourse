#include <pthread.h>  
#include <iostream>
#include <unistd.h>
#include <fstream>
#include <string>
#include <sstream>
#include <vector>

pthread_cond_t data_cond;
pthread_mutex_t data_mutex;
int data;
bool end = false;
bool change = false;

void* producer_routine(void* arg) {
    std::vector<int>*v = static_cast<std::vector<int>*>(arg);
    for (auto const& number: *v)
    {
      pthread_mutex_lock(&data_mutex);
      data = number;
      change = true;
      pthread_cond_signal(&data_cond);
      pthread_cond_wait(&data_cond, &data_mutex);
      pthread_mutex_unlock(&data_mutex);
    }

    pthread_mutex_lock(&data_mutex);
    end = true;
    pthread_cond_signal(&data_cond);
    pthread_mutex_unlock(&data_mutex);
}

void* consumer_routine(void* arg) {
  pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
  int& sum = *(int*) arg;
  while(!end){
      pthread_mutex_lock(&data_mutex);
      if (!change){
          pthread_cond_wait(&data_cond, &data_mutex);
      }
      if (!end){
          change = false;
          sum += data;
      }
      pthread_cond_signal(&data_cond);
      pthread_mutex_unlock(&data_mutex);
    }
}

void* consumer_interruptor_routine(void* arg) {
    pthread_t consumer = *(pthread_t*) arg;
    while(!end){
      pthread_cancel(consumer);
    }
}

std::vector<int> read_vector(){
  std::ifstream in_file("in.txt");
    std::string line;
    std::getline(in_file, line);
    std::stringstream iss(line);
    int number;
    std::vector<int> v;
    while(iss >> number){
      v.push_back(number);
    }
    return v;
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
    return sum;
}

int main() {
    std::cout << run_threads();
    return 0;
}
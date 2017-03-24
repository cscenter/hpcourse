#include <pthread.h>  
#include <vector>
#include <iostream>
using namespace std;

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

pthread_cond_t prod, consStartCond, prodStartCond;
bool f = false, finish = false;
bool consStart = false;
pthread_mutex_t m, m_s, m_e;

void* producer_routine(void* arg) {
	Value *v = (Value*) arg;
    int t;
	
    vector<int> vec;
	while(cin >> t) 
	{
       vec.push_back(t);
    }
	
    pthread_mutex_lock(&m);
    for (int i = 0; i < vec.size(); i++) {
		
        v->update(vec[i]);
		
        f = true;
		
        pthread_cond_broadcast(&prod);
		
		if(i != vec.size() - 1)
		{
			while(f) 
			{
				pthread_cond_wait(&prod, &m);
			}
		}
    }
	//pthread_mutex_lock(&m_e);
	finish = true;
	//pthread_mutex_unlock(&m_e);
    pthread_mutex_unlock(&m);

    pthread_exit(NULL);

}

void* consumer_routine(void* arg) {
	pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    Value *v = (Value*)arg;

	
    pthread_mutex_lock(&m_s);
    consStart = true;
    pthread_cond_broadcast(&consStartCond);
    pthread_mutex_unlock(&m_s);
	
	int* res = new int;
	*res = 0;
	
    pthread_mutex_lock(&m);
    while (true) {
        while (!f && !finish) 
		{
            pthread_cond_wait(&prod, &m);
        }
		
        *res += v->get();
		
        f = false;
        pthread_cond_broadcast(&prod);

        if (finish) 
		{
			pthread_mutex_unlock(&m);
            pthread_exit(res);
        }
    }
	
}

void* consumer_interruptor_routine(void* arg) {
	pthread_mutex_lock(&m_s);
    while(!consStart) 
	{
        pthread_cond_wait(&consStartCond, &m_s);
    }
    pthread_mutex_unlock(&m_s);

    while (true) 
	{
		//pthread_mutex_lock(&m_e);
		if (finish) break;
		pthread_cancel(*(pthread_t*)(arg));
		//pthread_mutex_unlock(&m_e);
	}
    pthread_exit(NULL);
}

int run_threads() {
	pthread_mutex_init(&m, NULL);
	pthread_mutex_init(&m_s, NULL);
	pthread_mutex_init(&m_e, NULL);
	pthread_cond_init(&prod, NULL);
	pthread_cond_init(&consStartCond, NULL);
	pthread_cond_init(&prodStartCond, NULL);
	
    pthread_t thread1, thread2, thread3;
	void* res;
    Value v;
    pthread_create(&thread1, NULL, producer_routine, (void *)&v);
    pthread_create(&thread2, NULL, consumer_routine, (void *)&v);
    pthread_create(&thread3, NULL, consumer_interruptor_routine, (void *)&thread2);
    pthread_join(thread1, NULL);
    pthread_join(thread2, &res);
    pthread_join(thread3, NULL);
	
    return *((int*) res);
}

int main() {
    cout << run_threads() << endl;
    return 0;
}
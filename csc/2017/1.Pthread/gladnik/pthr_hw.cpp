#include <pthread.h>
#include <iostream>
#include <cstdlib>
#include <string>
#include <sstream>
#include <vector>

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

unsigned int producedCount = 0, consumedCount = 0, numsCount = 1;
bool consumerHasStarted = false;

Value *value = new Value();

void *producer_routine(void *arg) {
	Value *value = (Value *) arg;

	while (!consumerHasStarted) {
		sched_yield();
	}
	std::string input;
	std::getline(std::cin, input);
	std::stringstream stream(input);
	std::vector<int> nums;
	copy(std::istream_iterator<int, char>(stream), std::istream_iterator<int, char>(), back_inserter(nums));
	numsCount = nums.size();
	for (int i = 0; i < numsCount; i++) {
		while (producedCount - consumedCount == 1) {
			sched_yield();
		}
		value->update(nums[i]);
		++producedCount;
	}
	pthread_exit(0);
}

void *consumer_routine(void *arg) {
	Value *value = (Value *) arg;

	consumerHasStarted = true;
	int *sum = new int(0);
	while (numsCount - consumedCount > 0) {
		while (producedCount == consumedCount) {
			sched_yield();
		}
		*sum += value->get();
		++consumedCount;
	}
	pthread_exit((void *) sum);
}

void *consumer_interruptor_routine(void *arg) {
	pthread_t *consumer = (pthread_t *) arg;

	while (!consumerHasStarted) {
		sched_yield();
	}
	while (numsCount - consumedCount > 0) {
		pthread_cancel(*consumer);
	}
	pthread_exit(0);
}

int run_threads() {
	int *sum;
	pthread_t producer, consumer, interruptor;
	pthread_create(&producer, 0, &producer_routine, (void *) value);
	pthread_create(&consumer, 0, &consumer_routine, (void *) value);
	pthread_create(&interruptor, 0, &consumer_interruptor_routine, (void *) &consumer);
	pthread_join(producer, 0);
	pthread_join(consumer, (void **) &sum);
	pthread_join(interruptor, 0);
	return *sum;
}

int main() {
	std::cout << run_threads() << std::endl;
	return 0;
}

import subprocess
import random

NUMBER_AMOUNT = 1000
NUMBER_OF_TRIES = 10000
MIN_NUMBER_VALUE = -100
MAX_NUMBER_VALUE = 100

for x in range(0, NUMBER_OF_TRIES):
	numbers = [random.randint(MIN_NUMBER_VALUE, MAX_NUMBER_VALUE) for i in range(0, NUMBER_AMOUNT)]

	with open("in.txt", 'w') as f:
		for number in numbers:
			f.write(str(number) + ' ')
	numbers_sum = sum(numbers)

	p = subprocess.Popen("./lab", stdout=subprocess.PIPE, shell=True)
	(output, err) = p.communicate() 

	if int(output) == numbers_sum:
		print(output + ' == ' + str(numbers_sum))
	else:
		print(output + ' != ' + str(numbers_sum) + ' !!!!!!')
		break		
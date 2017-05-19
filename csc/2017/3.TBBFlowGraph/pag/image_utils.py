import numpy as np
import matplotlib
from path import Path

# Uncomment for Mac users
# matplotlib.use('TkAgg')

import matplotlib.pyplot as plt
from PIL import Image
from struct import unpack_from, pack


# Write to .dat file
def imwrite(path):
    image = np.asarray(Image.open(path), dtype=np.uint8)
    if path.endswith('.png'):
        image = image[:, :, 0:3]  # Remove alpha channel

    shape = image.shape
    path, ext = Path(path).splitext()
    with open(path + '.dat', 'wb') as f:
        f.write(pack("iii", *shape))
        f.write(image.flatten().tobytes())


# Read from .dat file
def imread(path):
    with open(path, 'rb') as f:
        data = f.read()
        shape = unpack_from("iii", data)
        array = np.frombuffer(data[12:], dtype=np.uint8).reshape(shape)
        return array


def show_image(image):
    plt.imshow(image)
    plt.show()

show_image(imread("data/chicken_out.dat"))
show_image(imread("data/hat_out.dat"))
show_image(imread("data/cheer_out.dat"))
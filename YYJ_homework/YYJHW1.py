import pygame
from sys import exit
import numpy as np
    
width = 800
height = 600
list = []
pygame.init()
screen = pygame.display.set_mode((width, height), 0, 32)

background_image_filename = 'image/curve_pattern.png'

background = pygame.image.load(background_image_filename).convert()
width, height = background.get_size()
screen = pygame.display.set_mode((width, height), 0, 32)
pygame.display.set_caption("ImagePolylineMouseButton")
  
BLACK = (  0,   0,   0)
WHITE = (255, 255, 255)
BLUE =  (  0,   0, 255)
GREEN = (  0, 255,   0)
RED =   (255,   0,   0)

old_pt = np.array([0, 0])
cur_pt = np.array([0, 0])
 
screen.fill(WHITE)
clock= pygame.time.Clock()

done = False
pressed = -1
margin = 8
while not done:   
    time_passed = clock.tick(30)

    for event in pygame.event.get():
        if event.type == pygame.MOUSEBUTTONDOWN:
            pressed = 1            
        elif event.type == pygame.MOUSEMOTION:
            pressed = 0
        elif event.type == pygame.MOUSEBUTTONUP:
            pressed = 2            
        elif event.type == pygame.QUIT:
            done = True
        else:
            pressed = -1

    button1, button2, button3 = pygame.mouse.get_pressed()
    x,y = pygame.mouse.get_pos()
    if len(list)>1:
        for i in range(1,len(list)):
            pygame.draw.line(screen, GREEN, list[i-1], list[i], 5)

    if pressed == 1:
        if button1 == 1:
            x1, y1 = pygame.mouse.get_pos()
            cur_pt = np.array([x1, y1])
            list.append(cur_pt)
            pygame.draw.rect(screen, BLUE, (cur_pt[0]-margin, cur_pt[1]-margin, 2*margin, 2*margin), 5)
        elif button3 == 1:
            x1, y1 = pygame.mouse.get_pos()
            cur_pt = np.array([x1, y1])
            list.append(cur_pt)
            pygame.draw.rect(screen, RED, (cur_pt[0]-margin, cur_pt[1]-margin, 2*margin, 2*margin), 5)
        elif button2 == 1:
            x1, y1 = pygame.mouse.get_pos()
            cur_pt = np.array([x1, y1])
            list.append(cur_pt)
            pygame.draw.rect(screen, BLACK, (cur_pt[0]-margin, cur_pt[1]-margin, 2*margin, 2*margin), 5)

    print("mouse x:"+repr(x)+" y:"+repr(y)+" button:"+repr(button1)+" "+repr(button2)+" "+repr(button3)+" pressed:"+repr(pressed))
    old_pt = cur_pt   

    pygame.display.update()

pygame.quit()

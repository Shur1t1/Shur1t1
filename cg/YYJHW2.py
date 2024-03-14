"""
Modified on Feb 20 2020
@author: lbg@dongseo.ac.kr 
"""

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
  
# Define the colors we will use in RGB format
BLACK = (  0,   0,   0)
WHITE = (255, 255, 255)
BLUE =  (  0,   0, 255)
GREEN = (  0, 255,   0)
RED =   (255,   0,   0)

old_pt = np.array([0, 0])
cur_pt = np.array([0, 0])
myfont2 = pygame.font.Font(None, 15)
 
#screen.blit(background, (0,0))
screen.fill(WHITE)

# https://kite.com/python/docs/pygame.Surface.blit
clock= pygame.time.Clock()

#Loop until the user clicks the close button.
done = False
pressed = -1
margin = 8
Aim = False
a=0
AimSpeed = 5

def AnimationFunction(a,color='Red', thick=5):
    print(a)
    for i in range(len(list)-1):
        x = list[i][0]*(1-a) +list[i+1][0]*a
        y = list[i][1]*(1-a) +list[i+1][1]*a
        poc2 = [x,y]
        pygame.draw.circle(screen, color,poc2, 5)
        xs =  format(x, '.1f')
        ys =  format(y, '.1f')
        textImage = myfont2.render("("+xs+","+ys+")", True, color)
        screen.blit(textImage, (x-30, y-20))    
        
while not done:   
    # This limits the while loop to a max of 10 times per second.
    # Leave this out and we will use all CPU we can.
    time_passed = clock.tick(60)
    time_passed_seconds = time_passed/10000.0
    screen.fill(WHITE)
    for event in pygame.event.get():
        if event.type == pygame.MOUSEBUTTONDOWN:
            pressed = 1            
        elif event.type == pygame.MOUSEMOTION:
            pressed = 0
        elif event.type == pygame.MOUSEBUTTONUP:
            pressed = 2            
        elif event.type == pygame.QUIT:
            done = True
        elif event.type == pygame.KEYDOWN:
            if Aim:
                Aim = False
            else:
                a=0
                Aim = True
        else:
            pressed = -1

    button1, button2, button3 = pygame.mouse.get_pressed()
    x,y = pygame.mouse.get_pos()
    if len(list)>1:
        for i in range(len(list)-1):
            for j in range(1000):
                pox1 = list[i][0]+(list[i+1][0]-list[i][0])/1000*j
                poy1 = (list[i+1][1]-list[i][1])/(list[i+1][0]-list[i][0])*(pox1-list[i][0])+list[i][1]
                poc1 = [pox1,poy1]
                pygame.draw.circle(screen, GREEN,poc1, 2)


    if Aim:
        AnimationFunction(a)
        a = a + time_passed_seconds*AimSpeed
        if a >= 1:
            a = 0
            Aim = False
    elif a!=0:
        AnimationFunction(a)      

    if pressed == 1:
        if button1 == 1:
            x1, y1 = pygame.mouse.get_pos()
            cur_pt = np.array([x1, y1])
            list.append(cur_pt)
        elif button3 == 1:
            x1, y1 = pygame.mouse.get_pos()
            cur_pt = np.array([x1, y1])
            list.append(cur_pt)
        elif button2 == 1:
            x1, y1 = pygame.mouse.get_pos()
            cur_pt = np.array([x1, y1])
            list.append(cur_pt)

    for i in range(len(list)):
        pygame.draw.rect(screen, BLUE, (list[i][0]-margin, list[i][1]-margin, 2*margin, 2*margin), 5)

    print("mouse x:"+repr(x)+" y:"+repr(y)+" button:"+repr(button1)+" "+repr(button2)+" "+repr(button3)+" pressed:"+repr(pressed))
    old_pt = cur_pt   

    # Go ahead and update the screen with what we've drawn.
    # This MUST happen after all the other drawing commands.
    pygame.display.update()

pygame.quit()

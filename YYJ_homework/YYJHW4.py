import pygame
from sys import exit
import numpy as np
    
width = 800
height = 600
pygame.init()
screen = pygame.display.set_mode((width, height), 0, 32)

background_image_filename = 'image/curve_pattern.png'

background = pygame.image.load(background_image_filename).convert()
width, height = background.get_size()
screen = pygame.display.set_mode((width, height), 0, 32)
pygame.display.set_caption("ImagePolylineMouseButton")
  
BLACK = (  0,   0,   0)
Gary = (200, 200, 200)
lGary = (230, 230, 230)
WHITE = (255, 255, 255)
BLUE =  (  0,   0, 255)
GREEN = (  0, 255,   0)
RED =   (255,   0,   0)

pts = [] 
time = []
Trect = []
pos1 =[20,height-30]
pos2 =[width-20,height-30]
dis = 30
count = 0
myfont = pygame.font.Font(None, 30)
myfont2 = pygame.font.Font(None, 15)

screen.fill(WHITE)

clock= pygame.time.Clock()

class DrawText:
    def BarycentricCoordinatesType(text,color, surface, x, y):
        textImage = myfont2.render(text, True, color)
        surface.blit(textImage, (x-30, y-40))

    def coordinateType(xt,yt,color, surface,x,y,Decimal=1):
        dec = (str)(Decimal)
        xp = format(xt,'.'+ dec +'f')
        yp = format(yt,'.'+ dec +'f')
        textImage = myfont2.render("("+xp+","+yp+")", True, color)
        surface.blit(textImage, (x-30, y-20))

def drawPoint(pt, color='GREEN', thick=3):
    pygame.draw.circle(screen, color, pt, thick)

def drawLine(pt0, pt1, color='GREEN', thick=3):
    drawPoint((100,100), color,  thick)
    drawPoint(pt0, color, thick)
    drawPoint(pt1, color, thick)

def drawrect(pt, color=RED, pointFill=2):
    point = pygame.Rect(pt[0]-margin, pt[1]-margin, 2*margin, 2*margin)
    pygame.draw.rect(screen, color, point,pointFill)

def drawPolylines(color='GREEN', thick=3):
    if(count < 2): return
    for i in range(count-1):
        for j in range(dis):
                pox1 = pts[i][0]+(pts[i+1][0]-pts[i][0])/dis*j
                if pts[i+1][0]-pts[i][0] == 0:
                    poy1 = pts[i][1]+(pts[i+1][1]-pts[i][1])/dis*j
                else:
                    poy1 = (pts[i+1][1]-pts[i][1])/(pts[i+1][0]-pts[i][0])*(pox1-pts[i][0])+pts[i][1]
                poc1 = [pox1,poy1]
                
                pygame.draw.line(screen, color,pts[i],pts[i+1], thick)                

def drawCurve(color=GREEN, thick=3):  
    LagrangeInterpolation(color,thick) 


def AnimationFunction(a,color=RED, thick=5):
    if count>=3:
        DrawAniLagrange(a)

    else:
        for i in range(count-1):
            DrawAniPoint(pts[i],pts[i+1],a,True)      

def DrawAniPoint(P1,P2,a,show,line=False,color=RED, thick=5,max=1):
    x = P1[0]*(1-a)*max +P2[0]*a*max
    y = P1[1]*(1-a)*max +P2[1]*a*max
    pos = [x,y]
    drawPoint(pos, color, thick)
    if show:
        DrawText.coordinateType(x,y,color,screen,x,y,1)

def posWithTime(P1,P2,a):
    x = P1[0]*(1-a) +P2[0]*a
    y = P1[1]*(1-a) +P2[1]*a
    pos = [x,y]
    return pos

def DrawAniLagrange(a,color='Black', thick=1):
    T = a*(count-1)
    showWorkingLine = []

    for k in range(count-1):
        pos1=LagrangeInterpolationModule(k+1,k,0)
        pos2=LagrangeInterpolationModule(k+1,k,count-1)
        pygame.draw.line(screen, color,pos1,pos2, thick)
        if k==0:
            continue
        for i in range(count-k):
            pos=LagrangeInterpolationModule(i+k,i,T)
            showWorkingLine.append(pos)

    for i in  range(len(showWorkingLine)-1):
        pygame.draw.line(screen,Gary,showWorkingLine[i],showWorkingLine[i+1], thick) 
        drawPoint(showWorkingLine[i+1], BLUE, 2)

    drawPoint(showWorkingLine[0], BLUE, 2)
    pos=LagrangeInterpolationModule(count-1,0,T)
    drawPoint(pos, RED, 3)

def LagrangeInterpolation(color=BLUE, thick=1):
    t=0
    while t<count-1:
        p1 = LagrangeInterpolationModule(count-1,0,t)
        p2 = LagrangeInterpolationModule(count-1,0,t+0.1)
        pygame.draw.line(screen,color,p1,p2, thick) 
        t+=0.1  


def LagrangeInterpolationModule(MaxT,MinT,t):
    if MaxT-MinT > 1:
        q1 = LagrangeInterpolationModule(MaxT-1,MinT,t)
        q2 = LagrangeInterpolationModule(MaxT,MinT+1,t)
        result = np.dot(((time[MaxT]-t)/(time[MaxT]-time[MinT])),q1) +np.dot(((t-time[MinT])/(time[MaxT]-time[MinT])),q2) 
        return result
    else:
        q1 = pts[MinT]
        q2 = pts[MaxT]
        result = np.dot((time[MaxT]-t)/(time[MaxT]-time[MinT]),q1) +np.dot( (t-time[MinT])/(time[MaxT]-time[MinT]),q2) 
        return result

done = False
PopoutMenu = False
ButtonCheck = False
MenuPos =[0,0]
pressed1 = 0
pressed3 = 0
margin = 6
old_pressed1 = 0
old_pressed3 = 0
old_button1 = 0
old_button3 = 0
checkPressedPoint = -1
Aim = False
a = 0
AimSpeed = 5

while not done:   
    time_passed = clock.tick(60)
    time_passed_seconds = time_passed/10000.0
    screen.fill(WHITE)
    for event in pygame.event.get():
        if event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
            pressed1 = -1            
        elif event.type == pygame.MOUSEBUTTONUP and event.button == 1:
            pressed1 = 1            
        elif event.type == pygame.MOUSEBUTTONDOWN and event.button == 3:
            pressed3 = -1            
        elif event.type == pygame.MOUSEBUTTONUP and event.button == 3:
            pressed3 = 1            
        elif event.type == pygame.QUIT:
            done = True
        elif event.type == pygame.KEYUP and event.key == pygame.K_SPACE:
            if Aim:
                Aim = False
            else:
                a=0
                Aim = True
        elif event.type is pygame.QUIT:
            pygame.quit()

    button1, button2, button3 = pygame.mouse.get_pressed()
    x, y = pygame.mouse.get_pos()
    pt = [x, y]
    pygame.draw.circle(screen, RED, pt, 0)

    if old_pressed1 == -1 and pressed1 == 1 and old_button1 == 1 and button1 == 0 :
        pts.append(pt) 
        time.append(count)
        count += 1
        if len(time)>=3:
            Trect.append(posWithTime(pos1,pos2,time[count-2]/time[count-1]))
            for i in  range(len(Trect)-1):
                Trect[i]=posWithTime(pos1,pos2,time[i+1]/time[count-1])
    elif  old_pressed3 == -1 and pressed3 == 1 and old_button3 == 1 and button3 == 0 and checkPressedPoint==-1:
        if PopoutMenu :
            PopoutMenu = False
        else:
            PopoutMenu = True
            MenuPos = pt
    elif  pressed1 == -1 and ButtonCheck==False :
        PopoutMenu = False

    for i in range(count):
        pygame.draw.rect(screen, BLUE, (pts[i][0]-margin, pts[i][1]-margin, 2*margin, 2*margin), 5)
        x = format(pts[i][0], '.1f')
        y = format(pts[i][1], '.1f')
        textImage = myfont2.render("("+x+","+y+")", True, BLACK)
        screen.blit(textImage, (pts[i][0]-30, pts[i][1]-20))

    if len(pts)>1:
        drawPolylines(GREEN, 1)
        if len(pts)>2:
            drawCurve(BLUE,1)
    
    if Aim:
        AnimationFunction(a)
        a = a + time_passed_seconds*AimSpeed
        if a >= 1:
            a = 0
            Aim = False
    elif a!=0:
        AnimationFunction(a)

    pygame.display.update()
    old_button1 = button1
    old_button3 = button3
    old_pressed1 = pressed1
    old_pressed3 = pressed3

pygame.quit()

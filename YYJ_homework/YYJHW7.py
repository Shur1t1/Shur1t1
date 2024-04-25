import pygame
import pygame.freetype
from pygame.sprite import Sprite
import numpy as np
from sys import exit
    
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
Btime = []
Trect = []
pos1 =[20,height-30]
pos2 =[width-20,height-30]
dis = 30
count = 0
myfont = pygame.font.Font(None, 30)
myfont2 = pygame.font.Font(None, 15)

screen.fill(WHITE)

clock= pygame.time.Clock()

def FreeSystem(color=BLUE, thick=3):
    pygame.draw.line(screen, color, pos1, pos2, thick)
    pygame.draw.circle(screen, color, pos1, thick)
    pygame.draw.circle(screen, color, pos2, thick)


class DrawText:
    def coordinateType(xt, yt, color, surface, x, y, Decimal=1):
        dec = (str)(Decimal)
        xp = format(xt, '.' + dec + 'f')
        yp = format(yt, '.' + dec + 'f')
        textImage = myfont2.render("(" + xp + "," + yp + ")", True, color)
        surface.blit(textImage, (x - 30, y - 20))

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
    CubicBSpline(color, thick)

def AnimationFunction(a,color=RED, thick=5):
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

def CubicBSpline(color=BLUE, thick=1):
    global Btime
    if len(Btime)!=len(CubicBSplineTimeB()):
        Btime = CubicBSplineTimeB()
    cpos =CubicBSplineModule(Btime)
    for i in range(len(cpos)-1):
        pygame.draw.line(screen, color, cpos[i], cpos[i+1], thick)

def CubicBSplineTimeB():
    bt=[]
    bt.append(0)
    bt.append(0)
    bt.append(0)
    for i in range(len(pts)-1):
        bt.append(i)
    bt.append(len(pts)-2)
    bt.append(len(pts)-2)
    bt.append(len(pts)-2)
    return bt

def CubicBSplineModule(bt):
    l =0.01*(len(pts)-1)
    B=0
    cpos =[]
    Bt =bt
    t=Bt[3]
    cpos.append(pts[0])
    while t<Bt[len(Bt)-4]:
        
        xvalue =0
        yvalue =0
        for i in range (len(pts)-2):
            if Btime[i+3]<=t and t<Btime[i+4]:
                B=1
            else:
                B=0
            xvalue += (((Btime[i+4]-t)/(Btime[i+4]-Btime[i+3]))*((Btime[i+4]-t)/(Btime[i+4]-Btime[i+2])*pts[i][0]+(t-Btime[i+2])/(Btime[i+4]-Btime[i+2])*pts[i+1][0]) 
            + ((t-Btime[i+3])/(Btime[i+4]-Btime[i+3]))*((Btime[i+5]-t)/(Btime[i+5]-Btime[i+3])*pts[i+1][0]+(t-Btime[i+3])/(Btime[i+5]-Btime[i+3])*pts[i+2][0]))*B
            yvalue += (((Btime[i+4]-t)/(Btime[i+4]-Btime[i+3]))*((Btime[i+4]-t)/(Btime[i+4]-Btime[i+2])*pts[i][1]+(t-Btime[i+2])/(Btime[i+4]-Btime[i+2])*pts[i+1][1]) 
            + ((t-Btime[i+3])/(Btime[i+4]-Btime[i+3]))*((Btime[i+5]-t)/(Btime[i+5]-Btime[i+3])*pts[i+1][1]+(t-Btime[i+3])/(Btime[i+5]-Btime[i+3])*pts[i+2][1]))*B
        t=t+l
        point =[xvalue,yvalue]
        cpos.append(point)
    cpos.append(pts[count - 1])
    return cpos

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
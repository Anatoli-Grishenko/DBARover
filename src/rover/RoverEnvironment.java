/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rover;

import Environment.Environment;

/**
 *
 * @author Anatoli Grishenko <Anatoli.Grishenko@gmail.com>
 */
public class RoverEnvironment  extends Environment{
    public boolean started = false, cruiseAltitude = false, oriented = false, approaching = true, over = false, avoiding = false, left = false, right = false, blocked = false;
    public int goalDir, currentDir, goalPolar, currentPolar, steps, lowEnergy = 300, lastCompass = -1, watchLidar;
    public double lastDistance = -1;
    public String lastAction = "";
    
}

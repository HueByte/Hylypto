package com.hylypto.zombie;

public class PatrolConfig {
    // Group spawning
    public int defaultGroupSize = 5;
    public double spawnDistanceFromPlayer = 40.0;
    public String zombieModel = "Zombie";

    // State machine timings
    public float tickIntervalSeconds = 1.5f;
    public double waypointArrivalRadius = 5.0;
    public int swarmFormationSeconds = 5;

    // Detection
    public double detectionRange = 25.0;
    public double fovDegrees = 120.0;
    public double proximityAlwaysDetect = 5.0;
    public double aggroRange = 30.0;
    public int aggroTimeoutSeconds = 10;
    public int searchDurationSeconds = 20;

    // Despawning
    public double despawnDistanceFromPlayer = 50.0;

    // Screamer
    public boolean screamerEnabled = true;
    public double screamerDetectionRange = 20.0;
    public int screamerHordeSize = 8;
    public double screamerHordeSpawnDistance = 60.0;
    public String screamerModel = "Zombie_Aberrant";

    // Block breaking
    public boolean blockBreakEnabled = true;
    public float blockBreakIntervalSeconds = 3.0f;
    public int hitsToBreakDoor = 10;
}

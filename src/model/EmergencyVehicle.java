// ============================================================
// EmergencyVehicle.java
//
// Represents an emergency vehicle (ambulance, fire truck, or
// police car) present in a lane. Its presence triggers the
// EMERGENCY_BONUS in the lane's priority computation, allowing
// it to preempt all regular traffic.
// ============================================================
public class EmergencyVehicle extends Vehicle {

    public enum ServiceType {
        AMBULANCE,
        FIRE_TRUCK,
        POLICE
    }

    private final ServiceType serviceType;

    public EmergencyVehicle(ServiceType serviceType) {
        super();
        this.serviceType = serviceType;
    }

    public ServiceType getServiceType() { return serviceType; }

    @Override
    public String toString() {
        return serviceType.name() + "#" + getId();
    }
}
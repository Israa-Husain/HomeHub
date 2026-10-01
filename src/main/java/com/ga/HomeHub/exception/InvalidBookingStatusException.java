package com.ga.HomeHub.exception;

public class InvalidBookingStatusException extends RuntimeException{
    public InvalidBookingStatusException(String message){
        super(message);
    }
}

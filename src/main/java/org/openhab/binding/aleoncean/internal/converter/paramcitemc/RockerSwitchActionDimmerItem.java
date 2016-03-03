/*
 * Copyright (c) 2014 aleon GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Note for all commercial users of this library:
 * Please contact the EnOcean Alliance (http://www.enocean-alliance.org/)
 * about a possible requirement to become a member of the alliance to use the
 * EnOcean protocol implementations.
 */
package org.openhab.binding.aleoncean.internal.converter.paramcitemc;

import org.openhab.binding.aleoncean.internal.ActionIn;
import org.openhab.binding.aleoncean.internal.converter.ParameterClassItemClassConverter;
import org.openhab.binding.aleoncean.internal.devices.ItemInfo;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.util.concurrent.TimeUnit.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import eu.aleon.aleoncean.values.RockerSwitchAction;
    
public class RockerSwitchActionDimmerItem extends ParameterClassItemClassConverter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final Class<?> PARAMETER_CLASS = RockerSwitchAction.class;
    public static final Class<? extends Item> ITEM_CLASS = DimmerItem.class;

    public static final long SHORT_PRESS_TIMEOUT = 600; // 600 millisecond(s)
    public static final long MAX_DIM_DURATION = 10000; // 10 second(s)
    public static final long DIM_INTERVAL_MS = 300; // 200 milliseconds (ms)

    private long lastUpPressedMilliSec = 0;
    private long lastUpReleasedMilliSec = 0;
    private long lastDownPressedMilliSec = 0;
    private long lastDownReleasedMilliSec = 0;

    // TODO: use ESH scheduler
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private ScheduledFuture<?> dimmerHandle;

    public RockerSwitchActionDimmerItem(final ActionIn actionIn) {
        super(actionIn);
    }

    @Override
    public void commandFromOpenHAB(final EventPublisher eventPublisher,
                                   final String itemName, final ItemInfo itemInfo,
                                   final Command command) {
        // We map incoming rocker switch actions to control a dimmer item.
        // Incoming commands and states are ignored.
    }

    @Override
    public void stateFromOpenHAB(final EventPublisher eventPublisher,
                                 final String itemName, final ItemInfo itemInfo,
                                 final State state) {
        // We map incoming rocker switch actions to control a dimmer item.
        // Incoming commands and states are ignored.
    }

    @Override
    public void parameterFromDevice(final EventPublisher eventPublisher,
                                    final String itemName, final ItemInfo itemInfo,
                                    final Object value) {
        assert PARAMETER_CLASS.isAssignableFrom(value.getClass());

        final RockerSwitchAction action = (RockerSwitchAction) value;
        switch (getActionIn()) {
            case COMMAND:
            case DEFAULT:
                parameterFromDeviceCommand(eventPublisher, itemName, itemInfo, action);
                break;
            case STATE:
                logger.warn("This converter supports no state action.");
                break;
            default:
                logger.warn("Don't know how to handle action in type: {}", getActionIn());
                break;
        }
    }

    private void parameterFromDeviceCommand(final EventPublisher eventPublisher,
                                            final String itemName, final ItemInfo itemInfo,
                                            final RockerSwitchAction action) {
        final long curMilliSec = System.currentTimeMillis();

        switch (action) {
            case DIM_UP_PRESSED:
                lastUpPressedMilliSec = curMilliSec;
                unscheduleDimmingTask();
                scheduleDimmingTask(eventPublisher, itemName, IncreaseDecreaseType.INCREASE);
                break;
            case DIM_UP_RELEASED:
                unscheduleDimmingTask();
                lastUpReleasedMilliSec = curMilliSec;
                if (lastUpReleasedMilliSec - lastUpPressedMilliSec < SHORT_PRESS_TIMEOUT) {
                    postCommand(eventPublisher, itemName, OnOffType.ON);
                }
                break;
            case DIM_DOWN_PRESSED:
                lastDownPressedMilliSec = curMilliSec;
                unscheduleDimmingTask();
                scheduleDimmingTask(eventPublisher, itemName, IncreaseDecreaseType.DECREASE);
                break;
            case DIM_DOWN_RELEASED:
                unscheduleDimmingTask();
                lastDownReleasedMilliSec = curMilliSec;
                if (lastDownReleasedMilliSec - lastDownPressedMilliSec < SHORT_PRESS_TIMEOUT) {
                    postCommand(eventPublisher, itemName, OnOffType.OFF);
                }
                break;
            default:
                throw new AssertionError(action.name());
        }
    }

    private synchronized void scheduleDimmingTask(   final EventPublisher eventPublisher,
                                        final String itemName,
                                        final IncreaseDecreaseType incOrDec) {
        Runnable dimFunction = new Runnable(){
            public void run() {postCommand(eventPublisher, itemName, incOrDec);}};
        dimmerHandle = scheduler.scheduleAtFixedRate(dimFunction, SHORT_PRESS_TIMEOUT + 1, DIM_INTERVAL_MS, MILLISECONDS);
        scheduler.schedule(new Runnable() { public void run() { unscheduleDimmingTask(); }}
            , MAX_DIM_DURATION, MILLISECONDS);
    }

    private synchronized void unscheduleDimmingTask() {
        if (dimmerHandle != null) {
            dimmerHandle.cancel(true);
        }
    }

}

function(keys, values, rereduce) {
    return Math.max.apply(Math, values);
}
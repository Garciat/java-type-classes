package com.garciat.typeclasses.api.hkt;

public interface TApp<Tag extends Kind<Kind.KArr<Kind.KStar>>, A> extends Kind<Kind.KStar> {}
